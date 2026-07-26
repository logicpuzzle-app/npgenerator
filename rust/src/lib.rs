/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Rust rewrite derived from NPGenerator V2.0.2.
 *
 * Number Place Generator Version 2.0
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba
 * Programmer: Masaya Kiwada
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

mod block_split;
mod evaluator;
mod generator;
mod io;
mod model;
mod random;
mod solver;

use std::collections::HashMap;
use std::sync::Arc;

use generator::Generator;
use io::XmlData;
use model::{AnswerKind, BlockConstraint, SolverMethod, Status};
use random::JavaRandom;
use solver::{add_number, answer};
use wasm_bindgen::prelude::*;

const MIN_SIZE: usize = 2;
const MAX_SIZE: usize = 25;
const BLOCK_DEFAULT: u32 = 0;
const BLOCK_RECTANGLE: u32 = 1;
const BLOCK_RANDOM: u32 = 2;
const BLOCK_CUSTOM: u32 = 3;
const ALL_TECHNIQUES: u32 = (1 << 7) - 1;
const ALL_UNIQUENESS: u32 = (1 << 3) - 1;

#[derive(Clone, Copy)]
enum Symmetry {
    Rot4,
    Rot2,
    MirrorH,
    MirrorV,
    None,
}

impl Symmetry {
    fn from_code(code: u32) -> Result<Self, String> {
        match code {
            0 => Ok(Self::Rot4),
            1 => Ok(Self::Rot2),
            2 => Ok(Self::MirrorH),
            3 => Ok(Self::MirrorV),
            4 => Ok(Self::None),
            _ => Err("unknown symmetry".into()),
        }
    }

    fn orbit_size(self) -> usize {
        match self {
            Self::Rot4 => 4,
            Self::Rot2 | Self::MirrorH | Self::MirrorV => 2,
            Self::None => 1,
        }
    }

    fn maximum_hints(self, size: usize) -> usize {
        match self {
            Self::Rot4 | Self::Rot2 => size * size - size % 2,
            Self::MirrorH | Self::MirrorV => {
                size * size - if size % 2 == 0 { 0 } else { size }
            }
            Self::None => size * size - 1,
        }
    }

    fn is_fixed_point(self, size: usize, x: usize, y: usize) -> bool {
        if size % 2 == 0 {
            return false;
        }
        match self {
            Self::Rot4 | Self::Rot2 => x == size / 2 && y == size / 2,
            Self::MirrorH => x == size / 2,
            Self::MirrorV => y == size / 2,
            Self::None => false,
        }
    }
}

#[derive(Clone)]
struct Variant {
    size: usize,
    block: Arc<BlockConstraint>,
    block_labels: Vec<i32>,
    group_labels: Vec<Vec<i32>>,
    vertical: bool,
    horizontal: bool,
    default_block: bool,
    diagonal: bool,
}

/// The engine-neutral result returned by solve/generate/random.
#[derive(Clone, Debug)]
pub struct EngineResult {
    pub pattern: Vec<i32>,
    pub problem: Vec<i32>,
    pub solution: Vec<i32>,
    pub block_labels: Vec<i32>,
    pub group_labels: Vec<i32>,
    pub difficulty: f64,
    pub answer_kind: AnswerKind,
    pub vertical: bool,
    pub horizontal: bool,
    pub diagonal: bool,
    pub default_block: bool,
}

fn require_size(size: usize) -> Result<(), String> {
    if !(MIN_SIZE..=MAX_SIZE).contains(&size) {
        return Err(format!("size must be between {MIN_SIZE} and {MAX_SIZE}"));
    }
    Ok(())
}

fn validate_grid(values: &[i32], size: usize, pattern: bool, name: &str) -> Result<(), String> {
    if values.len() != size * size {
        return Err(format!("{name} must contain exactly {} cells", size * size));
    }
    for &value in values {
        if pattern {
            if value != 0 && value != 1 {
                return Err(format!("{name} cells must be 0 or 1"));
            }
        } else if value < 0 || value > size as i32 {
            return Err(format!("{name} cells must be between 0 and {size}"));
        }
    }
    Ok(())
}

fn rectangle_block_array(size: usize, width: usize, height: usize) -> Vec<i32> {
    let blocks_across = size / width;
    let mut labels = vec![0; size * size];
    for row in 0..size {
        for column in 0..size {
            labels[row * size + column] =
                ((row / height) * blocks_across + column / width + 1) as i32;
        }
    }
    labels
}

fn normalize_block_array(size: usize, labels: &[i32]) -> Result<Vec<i32>, String> {
    if labels.len() != size * size {
        return Err(format!(
            "block grid must contain exactly {} cells",
            size * size
        ));
    }
    let mut normalized = HashMap::new();
    let mut counts = vec![0; size];
    let mut result = Vec::with_capacity(labels.len());
    for &label in labels {
        let next = normalized.len() as i32 + 1;
        let normalized_label = *normalized.entry(label).or_insert(next);
        if normalized_label as usize > size {
            return Err(format!("block grid must contain exactly {size} blocks"));
        }
        result.push(normalized_label);
        counts[normalized_label as usize - 1] += 1;
    }
    if normalized.len() != size || counts.iter().any(|&count| count != size) {
        return Err(format!(
            "block grid must contain {size} blocks with {size} cells each"
        ));
    }
    Ok(result)
}

fn split_group_arrays(size: usize, labels: &[i32]) -> Result<Vec<Vec<i32>>, String> {
    let cells = size * size;
    if labels.len() % cells != 0 {
        return Err(format!(
            "additional group grids must each contain exactly {cells} cells"
        ));
    }
    Ok(labels.chunks_exact(cells).map(<[i32]>::to_vec).collect())
}

fn build_variant(
    size: usize,
    block_kind: u32,
    block_width: usize,
    block_height: usize,
    labels: &[i32],
    additional_group_labels: &[i32],
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    random: &mut JavaRandom,
) -> Result<Variant, String> {
    require_size(size)?;
    let additional_groups = split_group_arrays(size, additional_group_labels)?;
    let (block_labels, group_labels, default_block, rectangle) = match block_kind {
        BLOCK_DEFAULT => {
            let square = (size as f64).sqrt() as usize;
            if square * square != size {
                return Err(
                    "default blocks require a perfect-square size; choose rectangle or custom"
                        .into(),
                );
            }
            (
                rectangle_block_array(size, square, square),
                additional_groups,
                true,
                Some((square, square)),
            )
        }
        BLOCK_RECTANGLE => {
            if block_width == 0
                || block_height == 0
                || block_width * block_height != size
                || size % block_width != 0
                || size % block_height != 0
            {
                return Err("rectangle blocks require width * height == size".into());
            }
            (
                rectangle_block_array(size, block_width, block_height),
                additional_groups,
                false,
                Some((block_width, block_height)),
            )
        }
        BLOCK_RANDOM => {
            let labels = block_split::split_block(size, size, random);
            let mut groups = vec![labels.clone()];
            groups.extend(additional_groups);
            (labels, groups, false, None)
        }
        BLOCK_CUSTOM => {
            let labels = normalize_block_array(size, labels)?;
            let mut groups = vec![labels.clone()];
            groups.extend(additional_groups);
            (labels, groups, false, None)
        }
        _ => return Err("unknown block kind".into()),
    };
    let block = BlockConstraint::configured(
        size,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        rectangle,
        &group_labels,
    );
    Ok(Variant {
        size,
        block,
        block_labels,
        group_labels,
        vertical,
        horizontal,
        default_block,
        diagonal,
    })
}

fn solver_method(technique_mask: u32, uniqueness_mask: u32) -> SolverMethod {
    let mut method = SolverMethod::none();
    method.localization = technique_mask & (1 << 0) != 0;
    method.naked_pair = technique_mask & (1 << 1) != 0;
    method.hidden_pair = technique_mask & (1 << 2) != 0;
    method.naked_triple = technique_mask & (1 << 3) != 0;
    method.hidden_triple = technique_mask & (1 << 4) != 0;
    method.xwing = technique_mask & (1 << 5) != 0;
    method.swordfish = technique_mask & (1 << 6) != 0;
    method.unique.vh_unique = uniqueness_mask & (1 << 0) != 0;
    method.unique.cell_unique = uniqueness_mask & (1 << 1) != 0;
    method.unique.block_unique = uniqueness_mask & (1 << 2) != 0;
    method
}

fn solve_with_variant(
    problem: &[i32],
    method: SolverMethod,
    variant: &Variant,
) -> EngineResult {
    let mut status = Status::new(variant.size, Arc::clone(&variant.block));
    status.unique = method.unique;
    for (cell, &number) in problem.iter().enumerate() {
        if number > 0 {
            add_number(&mut status, cell, number);
        }
    }
    status = answer(status, method);
    let difficulty = if status.is_no_answer() {
        f64::NAN
    } else {
        evaluator::evaluate(variant.size, Arc::clone(&variant.block), problem)
    };
    EngineResult {
        pattern: problem.iter().map(|&value| i32::from(value != 0)).collect(),
        problem: problem.to_vec(),
        solution: status.cell,
        block_labels: variant.block_labels.clone(),
        group_labels: variant.group_labels.concat(),
        difficulty,
        answer_kind: status.kind,
        vertical: variant.vertical,
        horizontal: variant.horizontal,
        diagonal: variant.diagonal,
        default_block: variant.default_block,
    }
}

#[allow(clippy::too_many_arguments)]
pub fn solve_core(
    size: usize,
    problem: &[i32],
    block_kind: u32,
    block_width: usize,
    block_height: usize,
    block_labels: &[i32],
    additional_group_labels: &[i32],
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
) -> Result<EngineResult, String> {
    validate_grid(problem, size, false, "problem")?;
    let mut random = JavaRandom::new(seed);
    let variant = build_variant(
        size,
        block_kind,
        block_width,
        block_height,
        block_labels,
        additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        &mut random,
    )?;
    Ok(solve_with_variant(
        problem,
        solver_method(technique_mask, uniqueness_mask),
        &variant,
    ))
}

fn normalize_difficulty(mut minimum: i32, mut maximum: i32) -> (i32, i32) {
    minimum = minimum.max(0);
    if maximum < 0 {
        maximum = i32::MAX;
    }
    if minimum > maximum {
        std::mem::swap(&mut minimum, &mut maximum);
    }
    (minimum, maximum)
}

fn require_retry_limit(retry_limit: usize) -> Result<(), String> {
    if retry_limit == 0 {
        return Err("retry limit must be positive".into());
    }
    Ok(())
}

fn generate_with_variant(
    pattern: &[i32],
    hidden: &[i32],
    initial_seed: &[i32],
    random: &mut JavaRandom,
    method: SolverMethod,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    retry_limit: usize,
    variant: &Variant,
) -> Option<EngineResult> {
    let mut generator = Generator::new_with_seed(
        variant.size,
        pattern.to_vec(),
        hidden.to_vec(),
        Arc::clone(&variant.block),
        random,
        (!initial_seed.is_empty()).then(|| initial_seed.to_vec()),
    );
    generator.set_method(method);
    generator.set_forbidden(forbidden);
    for _ in 0..retry_limit {
        let Some(problem) = generator.generate() else {
            continue;
        };
        let solved = solve_with_variant(&problem, method, variant);
        if solved.difficulty < dp_min as f64 || solved.difficulty > dp_max as f64 {
            continue;
        }
        return Some(EngineResult {
            pattern: pattern.to_vec(),
            ..solved
        });
    }
    None
}

#[allow(clippy::too_many_arguments)]
pub fn generate_core(
    size: usize,
    pattern: &[i32],
    hidden: &[i32],
    initial_seed: &[i32],
    block_kind: u32,
    block_width: usize,
    block_height: usize,
    block_labels: &[i32],
    additional_group_labels: &[i32],
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    retry_limit: usize,
) -> Result<EngineResult, String> {
    validate_grid(pattern, size, true, "pattern")?;
    validate_grid(hidden, size, false, "hidden")?;
    if !initial_seed.is_empty() {
        validate_grid(initial_seed, size, false, "initial seed")?;
    }
    if forbidden != -1 && !(1..=size as i32).contains(&forbidden) {
        return Err(format!("forbidden number must be between 1 and {size}"));
    }
    require_retry_limit(retry_limit)?;
    let (dp_min, dp_max) = normalize_difficulty(dp_min, dp_max);
    let mut random = JavaRandom::new(seed);
    let variant = build_variant(
        size,
        block_kind,
        block_width,
        block_height,
        block_labels,
        additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        &mut random,
    )?;
    generate_with_variant(
        pattern,
        hidden,
        initial_seed,
        &mut random,
        solver_method(technique_mask, uniqueness_mask),
        dp_min,
        dp_max,
        forbidden,
        retry_limit,
        &variant,
    )
    .ok_or_else(|| format!("generation failed after {retry_limit} attempts"))
}

fn random_pattern(
    size: usize,
    hints: usize,
    random: &mut JavaRandom,
    symmetry: Symmetry,
) -> Vec<i32> {
    let mut pattern = vec![0; size * size];
    let mut count = 0;
    while count < hints {
        let x = random.next_int(size as i32) as usize;
        let y = random.next_int(size as i32) as usize;
        if symmetry.is_fixed_point(size, x, y) || pattern[y * size + x] != 0 {
            continue;
        }
        pattern[y * size + x] = 1;
        match symmetry {
            Symmetry::Rot4 => {
                pattern[(size - 1 - x) * size + y] = 1;
                pattern[(size - 1 - y) * size + size - 1 - x] = 1;
                pattern[x * size + size - 1 - y] = 1;
            }
            Symmetry::Rot2 => {
                pattern[(size - 1 - y) * size + size - 1 - x] = 1;
            }
            Symmetry::MirrorH => {
                pattern[y * size + size - 1 - x] = 1;
            }
            Symmetry::MirrorV => {
                pattern[(size - 1 - y) * size + x] = 1;
            }
            Symmetry::None => {}
        }
        count += symmetry.orbit_size();
    }
    pattern
}

#[allow(clippy::too_many_arguments)]
pub fn generate_random_core(
    size: usize,
    hints: usize,
    symmetry: u32,
    block_kind: u32,
    block_width: usize,
    block_height: usize,
    block_labels: &[i32],
    additional_group_labels: &[i32],
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    retry_limit: usize,
) -> Result<EngineResult, String> {
    require_size(size)?;
    require_retry_limit(retry_limit)?;
    let symmetry = Symmetry::from_code(symmetry)?;
    let maximum_hints = symmetry.maximum_hints(size);
    let orbit_size = symmetry.orbit_size();
    if hints == 0 || hints > maximum_hints || hints % orbit_size != 0 {
        if orbit_size == 1 {
            return Err(format!("hints must be between 1 and {maximum_hints}"));
        }
        return Err(format!(
            "hints must be a positive multiple of {orbit_size} no greater than {maximum_hints}"
        ));
    }
    if forbidden != -1 && !(1..=size as i32).contains(&forbidden) {
        return Err(format!("forbidden number must be between 1 and {size}"));
    }
    let (dp_min, dp_max) = normalize_difficulty(dp_min, dp_max);
    let method = solver_method(technique_mask, uniqueness_mask);
    let mut random = JavaRandom::new(seed);
    let variant = build_variant(
        size,
        block_kind,
        block_width,
        block_height,
        block_labels,
        additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        &mut random,
    )?;
    for _ in 0..retry_limit {
        let pattern = random_pattern(size, hints, &mut random, symmetry);
        if let Some(result) = generate_with_variant(
            &pattern,
            &vec![0; size * size],
            &[],
            &mut random,
            method,
            dp_min,
            dp_max,
            forbidden,
            retry_limit,
            &variant,
        ) {
            return Ok(result);
        }
    }
    Err(format!("generation failed after {retry_limit} attempts"))
}

/// JavaScript-facing engine result. Vector getters become Int32Array values.
#[wasm_bindgen]
pub struct WasmEngineResult {
    inner: EngineResult,
}

#[wasm_bindgen]
impl WasmEngineResult {
    #[wasm_bindgen(getter)]
    pub fn difficulty(&self) -> f64 {
        self.inner.difficulty
    }

    #[wasm_bindgen(getter)]
    pub fn answer_kind(&self) -> u32 {
        match self.inner.answer_kind {
            AnswerKind::Unique => 0,
            AnswerKind::NoAnswer => 1,
            AnswerKind::Multiple => 2,
            AnswerKind::Irregular => 3,
            AnswerKind::NoJudge => 4,
        }
    }

    #[wasm_bindgen(getter)]
    pub fn diagonal(&self) -> bool {
        self.inner.diagonal
    }

    #[wasm_bindgen(getter)]
    pub fn vertical(&self) -> bool {
        self.inner.vertical
    }

    #[wasm_bindgen(getter)]
    pub fn horizontal(&self) -> bool {
        self.inner.horizontal
    }

    #[wasm_bindgen(getter)]
    pub fn default_block(&self) -> bool {
        self.inner.default_block
    }

    pub fn pattern(&self) -> Vec<i32> {
        self.inner.pattern.clone()
    }

    pub fn problem(&self) -> Vec<i32> {
        self.inner.problem.clone()
    }

    pub fn solution(&self) -> Vec<i32> {
        self.inner.solution.clone()
    }

    pub fn block_labels(&self) -> Vec<i32> {
        self.inner.block_labels.clone()
    }

    pub fn group_labels(&self) -> Vec<i32> {
        self.inner.group_labels.clone()
    }
}

fn wasm_result(result: Result<EngineResult, String>) -> Result<WasmEngineResult, JsError> {
    result
        .map(|inner| WasmEngineResult { inner })
        .map_err(|message| JsError::new(&message))
}

#[wasm_bindgen]
#[allow(clippy::too_many_arguments)]
pub fn solve_puzzle(
    size: u32,
    problem: Vec<i32>,
    block_kind: u32,
    block_width: u32,
    block_height: u32,
    block_labels: Vec<i32>,
    additional_group_labels: Vec<i32>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
) -> Result<WasmEngineResult, JsError> {
    wasm_result(solve_core(
        size as usize,
        &problem,
        block_kind,
        block_width as usize,
        block_height as usize,
        &block_labels,
        &additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        seed,
        technique_mask,
        uniqueness_mask,
    ))
}

#[wasm_bindgen]
#[allow(clippy::too_many_arguments)]
pub fn generate_puzzle(
    size: u32,
    pattern: Vec<i32>,
    hidden: Vec<i32>,
    initial_seed: Vec<i32>,
    block_kind: u32,
    block_width: u32,
    block_height: u32,
    block_labels: Vec<i32>,
    additional_group_labels: Vec<i32>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    retry_limit: u32,
) -> Result<WasmEngineResult, JsError> {
    wasm_result(generate_core(
        size as usize,
        &pattern,
        &hidden,
        &initial_seed,
        block_kind,
        block_width as usize,
        block_height as usize,
        &block_labels,
        &additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        seed,
        technique_mask,
        uniqueness_mask,
        dp_min,
        dp_max,
        forbidden,
        retry_limit as usize,
    ))
}

#[wasm_bindgen]
#[allow(clippy::too_many_arguments)]
pub fn generate_random_puzzle(
    size: u32,
    hints: u32,
    symmetry: u32,
    block_kind: u32,
    block_width: u32,
    block_height: u32,
    block_labels: Vec<i32>,
    additional_group_labels: Vec<i32>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
    seed: i64,
    technique_mask: u32,
    uniqueness_mask: u32,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    retry_limit: u32,
) -> Result<WasmEngineResult, JsError> {
    wasm_result(generate_random_core(
        size as usize,
        hints as usize,
        symmetry,
        block_kind,
        block_width as usize,
        block_height as usize,
        &block_labels,
        &additional_group_labels,
        vertical,
        horizontal,
        diagonal,
        diagonal_last,
        seed,
        technique_mask,
        uniqueness_mask,
        dp_min,
        dp_max,
        forbidden,
        retry_limit as usize,
    ))
}

#[wasm_bindgen]
pub fn benchmark(count: u32, seed: i64) -> Result<u32, JsError> {
    if count == 0 {
        return Err(JsError::new("count must be positive"));
    }
    let mut random = JavaRandom::new(seed);
    let variant = build_variant(
        9,
        BLOCK_DEFAULT,
        0,
        0,
        &[],
        &[],
        true,
        true,
        false,
        false,
        &mut random,
    )
        .map_err(|message| JsError::new(&message))?;
    let mut succeeded = 0;
    for _ in 0..count {
        let mut result = None;
        for _ in 0..100 {
            let pattern = random_pattern(9, 20, &mut random, Symmetry::Rot4);
            result = generate_with_variant(
                &pattern,
                &[0; 81],
                &[],
                &mut random,
                SolverMethod::all(),
                0,
                i32::MAX,
                -1,
                100,
                &variant,
            );
            if result.is_some() {
                break;
            }
        }
        if result.is_some() {
            succeeded += 1;
        }
    }
    Ok(succeeded)
}

#[wasm_bindgen]
pub struct WasmXmlPuzzle {
    inner: XmlData,
}

#[wasm_bindgen]
impl WasmXmlPuzzle {
    #[wasm_bindgen(getter)]
    pub fn size(&self) -> u32 {
        self.inner.size as u32
    }

    #[wasm_bindgen(getter)]
    pub fn difficulty(&self) -> i32 {
        self.inner.difficult
    }

    #[wasm_bindgen(getter)]
    pub fn diagonal(&self) -> bool {
        self.inner.diagonal
    }

    #[wasm_bindgen(getter)]
    pub fn vertical(&self) -> bool {
        self.inner.vertical
    }

    #[wasm_bindgen(getter)]
    pub fn horizontal(&self) -> bool {
        self.inner.horizontal
    }

    #[wasm_bindgen(getter)]
    pub fn has_hint(&self) -> bool {
        self.inner.has_hint
    }

    #[wasm_bindgen(getter)]
    pub fn group_count(&self) -> u32 {
        self.inner.group_arrays.len() as u32
    }

    #[wasm_bindgen(getter)]
    pub fn comment(&self) -> String {
        self.inner.comment.clone().unwrap_or_default()
    }

    #[wasm_bindgen(getter)]
    pub fn default_block(&self) -> bool {
        self.inner.default_block
    }

    pub fn pattern(&self) -> Vec<i32> {
        self.inner.hint.clone()
    }

    pub fn hidden(&self) -> Vec<i32> {
        self.inner.hidden.clone()
    }

    pub fn problem(&self) -> Vec<i32> {
        self.inner.problem.clone()
    }

    pub fn solution(&self) -> Vec<i32> {
        self.inner.answer.clone()
    }

    pub fn block_labels(&self) -> Vec<i32> {
        self.inner.block_array.clone().unwrap_or_default()
    }

    pub fn group_labels(&self) -> Vec<i32> {
        self.inner.group_arrays.concat()
    }

    pub fn seed(&self) -> Vec<i32> {
        self.inner.seed.clone().unwrap_or_default()
    }
}

#[wasm_bindgen]
pub fn parse_npgen_xml(xml: &str) -> Result<WasmXmlPuzzle, JsError> {
    XmlData::from_xml_str(xml)
        .map(|inner| WasmXmlPuzzle { inner })
        .map_err(|message| JsError::new(&message))
}

#[wasm_bindgen]
#[allow(clippy::too_many_arguments)]
pub fn format_npgen_xml(
    size: u32,
    pattern: Vec<i32>,
    hidden: Vec<i32>,
    problem: Vec<i32>,
    solution: Vec<i32>,
    block_labels: Vec<i32>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    default_block: bool,
    difficulty: i32,
    comment: String,
) -> Result<String, JsError> {
    XmlData {
        size: size as usize,
        hint: pattern,
        has_hint: true,
        hidden,
        answer: solution,
        problem,
        block_array: (!default_block).then_some(block_labels),
        group_arrays: Vec::new(),
        seed: None,
        comment: (!comment.is_empty()).then_some(comment),
        vertical,
        horizontal,
        diagonal,
        default_block,
        difficult: difficulty,
    }
    .to_xml_string()
    .map_err(|message| JsError::new(&message))
}

/// Verification helper that renders XML constraints in the same form as the
/// Java reference driver's `blocks` command.
pub fn xml_constraint_dump(xml: &str) -> Result<String, String> {
    let source = XmlData::from_xml_str(xml)?;
    let rectangle = if source.default_block {
        let square = (source.size as f64).sqrt() as usize;
        if square * square != source.size {
            return Err("XML default-block requires a perfect-square size".into());
        }
        Some((square, square))
    } else {
        if source.group_arrays.is_empty() {
            return Err("XML custom block constraint is missing <group>".into());
        }
        None
    };
    let constraint = BlockConstraint::configured(
        source.size,
        source.vertical,
        source.horizontal,
        source.diagonal,
        true,
        rectangle,
        &source.group_arrays,
    );
    let mut output = format!("BLOCKS {}\n", constraint.blocks.len());
    for index in 0..constraint.blocks.len() {
        let line = constraint.blocks[index]
            .iter()
            .map(usize::to_string)
            .collect::<Vec<_>>()
            .join(" ");
        output.push_str(&line);
        output.push('\n');
    }
    Ok(output)
}

#[wasm_bindgen]
pub fn all_techniques_mask() -> u32 {
    ALL_TECHNIQUES
}

#[wasm_bindgen]
pub fn all_uniqueness_mask() -> u32 {
    ALL_UNIQUENESS
}

#[cfg(test)]
mod tests {
    use super::{
        generate_random_core, normalize_block_array, random_pattern, solve_core,
        JavaRandom, Symmetry, ALL_TECHNIQUES, ALL_UNIQUENESS, BLOCK_DEFAULT,
    };

    #[test]
    fn validates_custom_blocks() {
        assert!(normalize_block_array(2, &[7, 7, 9, 9]).is_ok());
        assert!(normalize_block_array(2, &[1, 1, 1, 2]).is_err());
    }

    #[test]
    fn solves_standard_grid() {
        let problem = [
            0, 0, 0, 7, 0, 0, 3, 0, 0, 0, 6, 0, 0, 0, 0, 5, 7, 0, 0, 5, 0, 0, 9,
            0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 4, 0, 0, 0, 6, 0, 0, 0, 5, 0,
            0, 0, 0, 0, 0, 0, 0, 9, 0, 0, 0, 8, 0, 0, 3, 0, 2, 1, 0, 0, 0, 0, 7,
            0, 0, 0, 4, 0, 0, 3, 0, 0, 5, 0,
        ];
        let result = solve_core(
            9,
            &problem,
            BLOCK_DEFAULT,
            0,
            0,
            &[],
            &[],
            true,
            true,
            false,
            false,
            0,
            ALL_TECHNIQUES,
            ALL_UNIQUENESS,
        )
        .unwrap();
        assert_eq!(result.solution.len(), 81);
    }

    #[test]
    fn rejects_invalid_random_hint_count() {
        assert!(generate_random_core(
            9,
            18,
            0,
            BLOCK_DEFAULT,
            0,
            0,
            &[],
            &[],
            true,
            true,
            false,
            false,
            1,
            ALL_TECHNIQUES,
            ALL_UNIQUENESS,
            0,
            -1,
            -1,
            100,
        )
        .is_err());
    }

    #[test]
    fn creates_requested_random_symmetries() {
        let mut random = JavaRandom::new(1);
        let rotational = random_pattern(9, 20, &mut random, Symmetry::Rot2);
        assert_eq!(rotational.iter().sum::<i32>(), 20);
        for index in 0..81 {
            assert_eq!(rotational[index], rotational[80 - index]);
        }

        let asymmetric = random_pattern(9, 7, &mut random, Symmetry::None);
        assert_eq!(asymmetric.iter().sum::<i32>(), 7);
    }

    #[test]
    fn rejects_zero_retry_limit() {
        assert!(generate_random_core(
            9,
            20,
            0,
            BLOCK_DEFAULT,
            0,
            0,
            &[],
            &[],
            true,
            true,
            false,
            false,
            1,
            ALL_TECHNIQUES,
            ALL_UNIQUENESS,
            0,
            -1,
            -1,
            0,
        )
        .is_err());
    }
}
