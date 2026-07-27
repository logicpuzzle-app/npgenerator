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
use std::env;
use std::path::Path;
use std::process::ExitCode;
use std::sync::Arc;
use std::time::Instant;

use generator::Generator;
use io::{format_grid, read_block_array, read_pattern, read_problem, XmlData};
use model::{AnswerKind, BlockConstraint, SolverMethod, Status};
use random::JavaRandom;
use solver::{add_number, answer};

const DEFAULT_SIZE: usize = 9;
const MIN_SIZE: usize = 2;
const MAX_SIZE: usize = 25;

#[derive(Clone, Copy)]
enum Symmetry {
    Rot4,
    Rot2,
    MirrorH,
    MirrorV,
    None,
}

impl Symmetry {
    fn parse(value: Option<&str>) -> Result<Self, String> {
        match value {
            None | Some("rot4") => Ok(Self::Rot4),
            Some("rot2") => Ok(Self::Rot2),
            Some("mirror-h") => Ok(Self::MirrorH),
            Some("mirror-v") => Ok(Self::MirrorV),
            Some("none") => Ok(Self::None),
            Some(_) => Err(
                "--symmetry must be rot4, rot2, mirror-h, mirror-v, or none"
                    .into(),
            ),
        }
    }

    fn option_name(self) -> &'static str {
        match self {
            Self::Rot4 => "rot4",
            Self::Rot2 => "rot2",
            Self::MirrorH => "mirror-h",
            Self::MirrorV => "mirror-v",
            Self::None => "none",
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

struct Solved {
    solution: Vec<i32>,
    difficulty: f64,
    kind: AnswerKind,
}

struct Generated {
    problem: Vec<i32>,
    solution: Vec<i32>,
    difficulty: f64,
}

struct RandomGenerated {
    pattern: Vec<i32>,
    generated: Generated,
}

struct Variant {
    size: usize,
    block: Arc<BlockConstraint>,
    block_array: Vec<i32>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    default_block: bool,
}

#[derive(Clone, Copy)]
struct CommandOptions {
    method: SolverMethod,
    dp_min: i32,
    dp_max: i32,
    forbidden: i32,
    attempts: usize,
}

impl Default for CommandOptions {
    fn default() -> Self {
        Self {
            method: SolverMethod::all(),
            dp_min: 0,
            dp_max: i32::MAX,
            forbidden: -1,
            attempts: 100,
        }
    }
}

struct ParsedOptions {
    values: HashMap<String, String>,
}

impl ParsedOptions {
    fn parse(
        args: &[String],
        start: usize,
        value_options: &[&str],
        flags: &[&str],
    ) -> Result<Self, String> {
        let mut values = HashMap::new();
        let mut index = start;
        while index < args.len() {
            let option = &args[index];
            if flags.contains(&option.as_str()) {
                values.insert(option.clone(), String::new());
                index += 1;
            } else if value_options.contains(&option.as_str()) {
                if index + 1 == args.len() {
                    return Err(format!("incomplete option: {option}"));
                }
                values.insert(option.clone(), args[index + 1].clone());
                index += 2;
            } else {
                return Err(format!("unknown option: {option}"));
            }
        }
        Ok(Self { values })
    }

    fn has(&self, option: &str) -> bool {
        self.values.contains_key(option)
    }

    fn value(&self, option: &str) -> Option<&str> {
        self.values.get(option).map(String::as_str)
    }

    fn i32_value(&self, option: &str, default: i32) -> Result<i32, String> {
        match self.value(option) {
            Some(value) => value
                .parse()
                .map_err(|_| format!("{option} requires an integer")),
            None => Ok(default),
        }
    }

    fn i64_value(&self, option: &str, default: i64) -> Result<i64, String> {
        match self.value(option) {
            Some(value) => value
                .parse()
                .map_err(|_| format!("{option} requires an integer")),
            None => Ok(default),
        }
    }

    fn usize_value(&self, option: &str, default: usize) -> Result<usize, String> {
        let value = self.i32_value(option, default as i32)?;
        usize::try_from(value).map_err(|_| format!("{option} requires an integer"))
    }
}

fn solve(
    problem: &[i32],
    method: SolverMethod,
    variant: &Variant,
) -> Solved {
    let mut status = Status::new(variant.size, Arc::clone(&variant.block));
    status.unique = method.unique;
    for (cell, &n) in problem.iter().enumerate() {
        if n > 0 {
            add_number(&mut status, cell, n);
        }
    }
    status = answer(status, method);
    let difficulty = if status.is_no_answer() {
        f64::NAN
    } else {
        evaluator::evaluate(variant.size, Arc::clone(&variant.block), problem)
    };
    Solved {
        solution: status.cell,
        difficulty,
        kind: status.kind,
    }
}

fn generate(
    pattern: &[i32],
    hidden: &[i32],
    initial_seed: Option<&[i32]>,
    random: &mut JavaRandom,
    options: CommandOptions,
    variant: &Variant,
) -> Option<Generated> {
    let mut generator = Generator::new_with_seed(
        variant.size,
        pattern.to_vec(),
        hidden.to_vec(),
        Arc::clone(&variant.block),
        random,
        initial_seed.map(<[i32]>::to_vec),
    );
    generator.set_method(options.method);
    generator.set_forbidden(options.forbidden);
    let mut attempt = 0;
    while options.attempts == 0 || attempt < options.attempts {
        attempt += 1;
        if let Some(problem) = generator.generate() {
            let solved = solve(&problem, options.method, variant);
            if solved.difficulty < options.dp_min as f64
                || (options.dp_max as f64) < solved.difficulty
            {
                continue;
            }
            return Some(Generated {
                problem,
                solution: solved.solution,
                difficulty: solved.difficulty,
            });
        }
    }
    None
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

fn generate_random(
    hints: usize,
    random: &mut JavaRandom,
    options: CommandOptions,
    variant: &Variant,
    symmetry: Symmetry,
) -> Option<RandomGenerated> {
    let mut attempt = 0;
    while options.attempts == 0 || attempt < options.attempts {
        attempt += 1;
        let pattern = random_pattern(variant.size, hints, random, symmetry);
        if let Some(generated) = generate(
            &pattern,
            &vec![0; variant.size * variant.size],
            None,
            random,
            options,
            variant,
        ) {
            return Some(RandomGenerated { pattern, generated });
        }
    }
    None
}

fn parse_list<'a>(value: &'a str, option: &str) -> Result<Vec<&'a str>, String> {
    if value.is_empty() {
        return Err(format!("{option} requires a non-empty list"));
    }
    let values: Vec<_> = value.split(',').collect();
    if values.iter().any(|item| item.is_empty()) {
        return Err(format!("{option} contains an empty value"));
    }
    Ok(values)
}

fn command_options(
    parsed: &ParsedOptions,
    allow_forbidden: bool,
    size: usize,
) -> Result<CommandOptions, String> {
    let mut options = CommandOptions::default();
    if let Some(value) = parsed.value("--use") {
        options.method.localization = false;
        options.method.naked_pair = false;
        options.method.hidden_pair = false;
        options.method.naked_triple = false;
        options.method.hidden_triple = false;
        options.method.xwing = false;
        options.method.swordfish = false;
        let names = parse_list(value, "--use")?;
        if names.contains(&"none") && names.len() != 1 {
            return Err("--use value none cannot be combined with other values".into());
        }
        for name in names {
            match name {
                "none" => {}
                "localization" => options.method.localization = true,
                "naked-pair" => options.method.naked_pair = true,
                "hidden-pair" => options.method.hidden_pair = true,
                "naked-triple" => options.method.naked_triple = true,
                "hidden-triple" => options.method.hidden_triple = true,
                "x-wing" => options.method.xwing = true,
                "swordfish" => options.method.swordfish = true,
                _ => return Err(format!("unknown --use value: {name}")),
            }
        }
    }
    if let Some(value) = parsed.value("--unique") {
        options.method.unique.vh_unique = false;
        options.method.unique.cell_unique = false;
        options.method.unique.block_unique = false;
        let names = parse_list(value, "--unique")?;
        if names.contains(&"none") && names.len() != 1 {
            return Err("--unique value none cannot be combined with other values".into());
        }
        for name in names {
            match name {
                "none" => {}
                "vh" => options.method.unique.vh_unique = true,
                "cell" => options.method.unique.cell_unique = true,
                "block" => options.method.unique.block_unique = true,
                _ => return Err(format!("unknown --unique value: {name}")),
            }
        }
    }
    options.dp_min = parsed.i32_value("--dp-min", 0)?.max(0);
    options.dp_max = parsed.i32_value("--dp-max", i32::MAX)?;
    if options.dp_max < 0 {
        options.dp_max = i32::MAX;
    }
    if options.dp_min > options.dp_max {
        std::mem::swap(&mut options.dp_min, &mut options.dp_max);
    }
    options.forbidden = parsed.i32_value("--forbidden", -1)?;
    if allow_forbidden
        && parsed.has("--forbidden")
        && !(1..=size as i32).contains(&options.forbidden)
    {
        return Err(format!("--forbidden must be between 1 and {size}"));
    }
    let attempts = parsed.i32_value("--attempts", 100)?;
    if attempts < 0 {
        return Err("--attempts must be non-negative".into());
    }
    options.attempts = attempts as usize;
    Ok(options)
}

fn require_size(size: usize) -> Result<(), String> {
    if !(MIN_SIZE..=MAX_SIZE).contains(&size) {
        return Err("--size must be between 2 and 25".into());
    }
    Ok(())
}

fn validate_values(
    values: &[i32],
    size: usize,
    pattern: bool,
    name: &str,
) -> Result<(), String> {
    if values.len() != size * size {
        return Err(format!(
            "{name} must contain exactly {} cells",
            size * size
        ));
    }
    for &value in values {
        if pattern {
            if value != 0 && value != 1 {
                return Err("pattern cells must be 0 or 1".into());
            }
        } else if value < 0 || value > size as i32 {
            return Err(format!(
                "{name} cells must be between 0 and {size}"
            ));
        }
    }
    Ok(())
}

fn rectangle_block_array(size: usize, width: usize, height: usize) -> Vec<i32> {
    let mut labels = vec![0; size * size];
    let blocks_across = size / width;
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
    let mut normalized_labels = HashMap::new();
    let mut result = Vec::with_capacity(labels.len());
    let mut counts = vec![0; size];
    for &label in labels {
        let normalized = match normalized_labels.get(&label) {
            Some(&value) => value,
            None => {
                if normalized_labels.len() == size {
                    return Err(format!(
                        "block grid must contain exactly {size} blocks"
                    ));
                }
                let value = normalized_labels.len() as i32 + 1;
                normalized_labels.insert(label, value);
                value
            }
        };
        result.push(normalized);
        counts[normalized as usize - 1] += 1;
    }
    if normalized_labels.len() != size {
        return Err(format!(
            "block grid must contain exactly {size} blocks"
        ));
    }
    if counts.iter().any(|&count| count != size) {
        return Err(format!(
            "every block must contain exactly {size} cells"
        ));
    }
    Ok(result)
}

fn build_array_variant(
    size: usize,
    labels: &[i32],
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    diagonal_last: bool,
) -> Result<Variant, String> {
    let labels = normalize_block_array(size, labels)?;
    Ok(Variant {
        size,
        block: BlockConstraint::configured(
            size,
            vertical,
            horizontal,
            diagonal,
            diagonal_last,
            None,
            &[labels.clone()],
        ),
        block_array: labels,
        vertical,
        horizontal,
        diagonal,
        default_block: false,
    })
}

fn build_variant(
    size: usize,
    block_spec: Option<&str>,
    vertical: bool,
    horizontal: bool,
    diagonal: bool,
    random: &mut JavaRandom,
    diagonal_last: bool,
) -> Result<Variant, String> {
    require_size(size)?;
    match block_spec {
        None => {
            let square = (size as f64).sqrt() as usize;
            if square * square != size {
                return Err(
                    "--blocks is required when --size is not a perfect square".into(),
                );
            }
            Ok(Variant {
                size,
                block: BlockConstraint::configured(
                    size,
                    vertical,
                    horizontal,
                    diagonal,
                    diagonal_last,
                    Some((square, square)),
                    &[],
                ),
                block_array: rectangle_block_array(size, square, square),
                vertical,
                horizontal,
                diagonal,
                default_block: true,
            })
        }
        Some("random") => {
            let labels = block_split::split_block(size, size, random);
            build_array_variant(
                size,
                &labels,
                vertical,
                horizontal,
                diagonal,
                diagonal_last,
            )
        }
        Some(spec) if spec.starts_with('@') => {
            if spec.len() == 1 {
                return Err("--blocks @file requires a file name".into());
            }
            let labels = read_block_array(Path::new(&spec[1..]), size)?;
            build_array_variant(
                size,
                &labels,
                vertical,
                horizontal,
                diagonal,
                diagonal_last,
            )
        }
        Some(spec) => {
            let dimensions: Vec<_> = spec.to_ascii_lowercase().split('x').map(str::to_owned).collect();
            if dimensions.len() != 2 {
                return Err("--blocks must be WxH, random, or @file.txt".into());
            }
            let width: usize = dimensions[0]
                .parse()
                .map_err(|_| "--blocks WxH requires integer dimensions")?;
            let height: usize = dimensions[1]
                .parse()
                .map_err(|_| "--blocks WxH requires integer dimensions")?;
            if width == 0
                || height == 0
                || width * height != size
                || size % width != 0
                || size % height != 0
            {
                return Err("--blocks WxH requires W*H == size".into());
            }
            Ok(Variant {
                size,
                block: BlockConstraint::configured(
                    size,
                    vertical,
                    horizontal,
                    diagonal,
                    diagonal_last,
                    Some((width, height)),
                    &[],
                ),
                block_array: rectangle_block_array(size, width, height),
                vertical,
                horizontal,
                diagonal,
                default_block: false,
            })
        }
    }
}

fn build_xml_variant(
    source: &XmlData,
    force_diagonal: bool,
    no_vertical: bool,
    no_horizontal: bool,
) -> Result<Variant, String> {
    require_size(source.size)?;
    let vertical = source.vertical && !no_vertical;
    let horizontal = source.horizontal && !no_horizontal;
    let diagonal = source.diagonal || force_diagonal;
    let (block_array, rectangle) = if source.default_block {
        let square = (source.size as f64).sqrt() as usize;
        if square * square != source.size {
            return Err("XML default-block requires a perfect-square size".into());
        }
        (
            rectangle_block_array(source.size, square, square),
            Some((square, square)),
        )
    } else {
        let labels = source
            .block_array
            .as_ref()
            .ok_or("XML custom block constraint is missing <group>")?;
        (labels.clone(), None)
    };
    Ok(Variant {
        size: source.size,
        block: BlockConstraint::configured(
            source.size,
            vertical,
            horizontal,
            diagonal,
            true,
            rectangle,
            &source.group_arrays,
        ),
        block_array,
        vertical,
        horizontal,
        diagonal,
        default_block: source.default_block,
    })
}

fn require_xml_format(parsed: &ParsedOptions) -> Result<(), String> {
    if parsed
        .value("--format")
        .is_some_and(|format| !format.eq_ignore_ascii_case("xml"))
    {
        return Err("--format only supports xml".into());
    }
    Ok(())
}

fn xml_output(parsed: &ParsedOptions) -> bool {
    parsed.has("--format") || parsed.has("--out")
}

fn is_xml(path: &Path) -> bool {
    path.file_name()
        .and_then(|name| name.to_str())
        .is_some_and(|name| name.to_ascii_lowercase().ends_with(".xml"))
}

fn write_xml(parsed: &ParsedOptions, data: &XmlData) -> Result<(), String> {
    match parsed.value("--out") {
        Some(path) => data.save(Path::new(path)),
        None => {
            print!("{}", data.to_xml_string()?);
            Ok(())
        }
    }
}

fn xml_file(
    variant: &Variant,
    hint: Vec<i32>,
    hidden: Vec<i32>,
    problem: Vec<i32>,
    answer: Vec<i32>,
    difficulty: f64,
    source: Option<&XmlData>,
) -> XmlData {
    XmlData {
        size: variant.size,
        hint,
        has_hint: true,
        hidden,
        answer,
        problem,
        block_array: Some(variant.block_array.clone()),
        group_arrays: if variant.default_block {
            Vec::new()
        } else {
            vec![variant.block_array.clone()]
        },
        seed: None,
        comment: source.and_then(|value| value.comment.clone()),
        vertical: variant.vertical,
        horizontal: variant.horizontal,
        diagonal: variant.diagonal,
        default_block: variant.default_block,
        difficult: difficulty as i32,
    }
}

fn print_solved(solved: &Solved, size: usize) {
    print!("SOLUTION\n{}", format_grid(&solved.solution, size, false));
    if solved.difficulty.is_finite() && solved.difficulty.fract() == 0.0 {
        println!("DIFFICULTY {:.1}", solved.difficulty);
    } else {
        println!("DIFFICULTY {}", solved.difficulty);
    }
}

fn print_generated(generated: &Generated, size: usize) {
    print!(
        "PROBLEM\n{}",
        format_grid(&generated.problem, size, false)
    );
    print_solved(
        &Solved {
            solution: generated.solution.clone(),
            difficulty: generated.difficulty,
            kind: AnswerKind::Unique,
        },
        size,
    );
}

const SOLVER_OPTIONS: &[&str] = &["--use", "--unique"];
const GENERATOR_OPTIONS: &[&str] = &[
    "--use",
    "--unique",
    "--dp-min",
    "--dp-max",
    "--attempts",
];
const VARIANT_OPTIONS: &[&str] = &["--size", "--blocks", "--seed", "--format", "--out"];
const VARIANT_FLAGS: &[&str] = &["--diagonal", "--no-vertical", "--no-horizontal"];

fn options<'a>(first: &[&'a str], second: &[&'a str]) -> Vec<&'a str> {
    first.iter().chain(second).copied().collect()
}

fn run(args: &[String]) -> Result<i32, String> {
    if args.is_empty() {
        eprintln!("usage: npgen solve|generate|random|bench ...");
        return Ok(2);
    }
    match args[0].as_str() {
        "solve" => {
            if args.len() < 2 {
                return Err(
                    "usage: npgen solve <problem> [--size N] [--blocks spec] \
                     [--diagonal] [--format xml] [--out file.xml]"
                        .into(),
                );
            }
            let parsed = ParsedOptions::parse(
                args,
                2,
                &options(SOLVER_OPTIONS, VARIANT_OPTIONS),
                VARIANT_FLAGS,
            )?;
            require_xml_format(&parsed)?;
            let mut random = JavaRandom::new(parsed.i64_value("--seed", 0)?);
            let input_path = Path::new(&args[1]);
            let xml_input = parsed.has("--format") || is_xml(input_path);
            let (problem, source, variant) = if xml_input {
                let source = XmlData::load(input_path)?;
                if parsed.has("--size")
                    && parsed.usize_value("--size", source.size)? != source.size
                {
                    return Err(format!(
                        "--size does not match XML problem size {}",
                        source.size
                    ));
                }
                let variant = if parsed.has("--blocks") {
                    build_variant(
                        source.size,
                        parsed.value("--blocks"),
                        source.vertical && !parsed.has("--no-vertical"),
                        source.horizontal && !parsed.has("--no-horizontal"),
                        source.diagonal || parsed.has("--diagonal"),
                        &mut random,
                        true,
                    )?
                } else {
                    build_xml_variant(
                        &source,
                        parsed.has("--diagonal"),
                        parsed.has("--no-vertical"),
                        parsed.has("--no-horizontal"),
                    )?
                };
                (source.problem.clone(), Some(source), variant)
            } else {
                let size = parsed.usize_value("--size", DEFAULT_SIZE)?;
                let variant = build_variant(
                    size,
                    parsed.value("--blocks"),
                    !parsed.has("--no-vertical"),
                    !parsed.has("--no-horizontal"),
                    parsed.has("--diagonal"),
                    &mut random,
                    false,
                )?;
                (read_problem(input_path, size)?, None, variant)
            };
            validate_values(&problem, variant.size, false, "problem")?;
            let command = command_options(&parsed, false, variant.size)?;
            let solved = solve(&problem, command.method, &variant);
            if matches!(solved.kind, AnswerKind::NoAnswer | AnswerKind::Irregular)
                || ((parsed.has("--use") || parsed.has("--unique"))
                    && solved.kind != AnswerKind::Unique)
            {
                let result = match solved.kind {
                    AnswerKind::NoAnswer => "NO_ANSWER",
                    AnswerKind::Irregular => "IRREGULAR_PROBLEM",
                    AnswerKind::Multiple => "MULTIPLE_ANSWER",
                    _ => return Err("unexpected unsuccessful solve status".into()),
                };
                eprintln!("RESULT {result}");
                return Ok(1);
            }
            if xml_output(&parsed) {
                let hidden = source
                    .as_ref()
                    .map(|value| value.hidden.clone())
                    .unwrap_or_else(|| vec![0; variant.size * variant.size]);
                let hint = source
                    .as_ref()
                    .filter(|value| value.has_hint)
                    .map(|value| value.hint.clone())
                    .unwrap_or_else(|| {
                        problem
                            .iter()
                            .map(|&value| i32::from(value != 0))
                            .collect()
                    });
                write_xml(
                    &parsed,
                    &xml_file(
                        &variant,
                        hint,
                        hidden,
                        problem,
                        solved.solution,
                        solved.difficulty,
                        source.as_ref(),
                    ),
                )?;
            } else {
                print_solved(&solved, variant.size);
            }
            Ok(0)
        }
        "generate" => {
            if args.len() < 2 {
                return Err("usage: npgen generate <pattern> [--seed N]".into());
            }
            let mut allowed = options(GENERATOR_OPTIONS, VARIANT_OPTIONS);
            allowed.push("--forbidden");
            let parsed =
                ParsedOptions::parse(args, 2, &allowed, VARIANT_FLAGS)?;
            require_xml_format(&parsed)?;
            let mut random = JavaRandom::new(parsed.i64_value("--seed", 0)?);
            let input_path = Path::new(&args[1]);
            let xml_input = parsed.has("--format") || is_xml(input_path);
            let (pattern, hidden, initial_seed, source, variant) = if xml_input {
                let source = XmlData::load(input_path)?;
                if parsed.has("--size")
                    && parsed.usize_value("--size", source.size)? != source.size
                {
                    return Err(format!(
                        "--size does not match XML problem size {}",
                        source.size
                    ));
                }
                let variant = if parsed.has("--blocks") {
                    build_variant(
                        source.size,
                        parsed.value("--blocks"),
                        source.vertical && !parsed.has("--no-vertical"),
                        source.horizontal && !parsed.has("--no-horizontal"),
                        source.diagonal || parsed.has("--diagonal"),
                        &mut random,
                        true,
                    )?
                } else {
                    build_xml_variant(
                        &source,
                        parsed.has("--diagonal"),
                        parsed.has("--no-vertical"),
                        parsed.has("--no-horizontal"),
                    )?
                };
                (
                    source.hint.clone(),
                    source.hidden.clone(),
                    source.seed.clone(),
                    Some(source),
                    variant,
                )
            } else {
                let size = parsed.usize_value("--size", DEFAULT_SIZE)?;
                let variant = build_variant(
                    size,
                    parsed.value("--blocks"),
                    !parsed.has("--no-vertical"),
                    !parsed.has("--no-horizontal"),
                    parsed.has("--diagonal"),
                    &mut random,
                    false,
                )?;
                (
                    read_pattern(input_path, size)?,
                    vec![0; size * size],
                    None,
                    None,
                    variant,
                )
            };
            validate_values(&pattern, variant.size, true, "pattern")?;
            validate_values(&hidden, variant.size, false, "hidden")?;
            let command = command_options(&parsed, true, variant.size)?;
            let Some(generated) =
                generate(
                    &pattern,
                    &hidden,
                    initial_seed.as_deref(),
                    &mut random,
                    command,
                    &variant,
                )
            else {
                eprintln!(
                    "RESULT GENERATE_FAILED attempts={}",
                    command.attempts
                );
                return Ok(1);
            };
            if xml_output(&parsed) {
                write_xml(
                    &parsed,
                    &xml_file(
                        &variant,
                        pattern.clone(),
                        hidden,
                        generated.problem.clone(),
                        generated.solution.clone(),
                        generated.difficulty,
                        source.as_ref(),
                    ),
                )?;
            } else {
                print_generated(&generated, variant.size);
            }
            Ok(0)
        }
        "random" => {
            let mut allowed = options(GENERATOR_OPTIONS, VARIANT_OPTIONS);
            allowed.extend(["--hints", "--forbidden", "--symmetry"]);
            let parsed =
                ParsedOptions::parse(args, 1, &allowed, VARIANT_FLAGS)?;
            require_xml_format(&parsed)?;
            let size = parsed.usize_value("--size", DEFAULT_SIZE)?;
            let hints = parsed.i32_value("--hints", 20)?;
            let symmetry = Symmetry::parse(parsed.value("--symmetry"))?;
            let mut random = JavaRandom::new(parsed.i64_value("--seed", 0)?);
            let variant = build_variant(
                size,
                parsed.value("--blocks"),
                !parsed.has("--no-vertical"),
                !parsed.has("--no-horizontal"),
                parsed.has("--diagonal"),
                &mut random,
                false,
            )?;
            let maximum_hints = symmetry.maximum_hints(size);
            let orbit_size = symmetry.orbit_size();
            if hints <= 0
                || hints as usize > maximum_hints
                || hints as usize % orbit_size != 0
            {
                if orbit_size == 1 {
                    return Err(format!(
                        "--hints must be between 1 and {maximum_hints} for --symmetry {}",
                        symmetry.option_name()
                    ));
                }
                return Err(format!(
                    "--hints must be a positive multiple of {orbit_size} \
                     no greater than {maximum_hints} for --symmetry {}",
                    symmetry.option_name()
                ));
            }
            let command = command_options(&parsed, true, size)?;
            let Some(result) =
                generate_random(
                    hints as usize,
                    &mut random,
                    command,
                    &variant,
                    symmetry,
                )
            else {
                eprintln!(
                    "RESULT GENERATE_FAILED attempts={}",
                    command.attempts
                );
                return Ok(1);
            };
            if xml_output(&parsed) {
                write_xml(
                    &parsed,
                    &xml_file(
                        &variant,
                        result.pattern.clone(),
                        vec![0; size * size],
                        result.generated.problem.clone(),
                        result.generated.solution.clone(),
                        result.generated.difficulty,
                        None,
                    ),
                )?;
            } else {
                print!("PATTERN\n{}", format_grid(&result.pattern, size, true));
                print_generated(&result.generated, size);
            }
            Ok(0)
        }
        "bench" => {
            let parsed =
                ParsedOptions::parse(args, 1, &["--count", "--seed"], &[])?;
            let count = parsed.i64_value("--count", 10)?;
            let seed = parsed.i64_value("--seed", 0)?;
            if count <= 0 {
                return Err("--count must be positive".into());
            }
            let mut random = JavaRandom::new(seed);
            let variant =
                build_variant(
                    DEFAULT_SIZE,
                    None,
                    true,
                    true,
                    false,
                    &mut random,
                    false,
                )?;
            let start = Instant::now();
            let mut succeeded = 0;
            for _ in 0..count {
                if generate_random(
                    20,
                    &mut random,
                    CommandOptions::default(),
                    &variant,
                    Symmetry::Rot4,
                )
                .is_some()
                {
                    succeeded += 1;
                }
            }
            println!("COUNT {count}");
            println!("SUCCEEDED {succeeded}");
            println!("ELAPSED_MS {}", start.elapsed().as_millis());
            Ok(if succeeded == count { 0 } else { 1 })
        }
        _ => {
            eprintln!("usage: npgen solve|generate|random|bench ...");
            Ok(2)
        }
    }
}

fn main() -> ExitCode {
    let args: Vec<_> = env::args().skip(1).collect();
    match run(&args) {
        Ok(code) => ExitCode::from(code as u8),
        Err(error) => {
            eprintln!("input error: {error}");
            ExitCode::from(2)
        }
    }
}
