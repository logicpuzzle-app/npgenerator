/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Rust rewrite derived from NPGenerator V2.0.2.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

use std::fs;
use std::path::Path;

pub fn read_problem(path: &Path, size: usize) -> Result<Vec<i32>, String> {
    read_grid(path, size, false)
}

pub fn read_pattern(path: &Path, size: usize) -> Result<Vec<i32>, String> {
    read_grid(path, size, true)
}

fn read_grid(path: &Path, size: usize, pattern: bool) -> Result<Vec<i32>, String> {
    let input = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let mut result = Vec::with_capacity(size * size);
    for line in input.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let tokens: Vec<_> = trimmed.split_whitespace().collect();
        if tokens.len() != size {
            continue;
        }
        let mut row = Vec::with_capacity(size);
        for token in tokens {
            let value = if token == "-" || token == "0" {
                Some(0)
            } else if pattern && token.eq_ignore_ascii_case("X") {
                Some(1)
            } else {
                token.parse::<i32>().ok().and_then(|value| {
                    (1..=size as i32)
                        .contains(&value)
                        .then_some(if pattern { 1 } else { value })
                })
            };
            match value {
                Some(value) => row.push(value),
                None => {
                    row.clear();
                    break;
                }
            }
        }
        if row.len() == size {
            result.extend(row);
            if result.len() == size * size {
                break;
            }
        }
    }
    if result.len() != size * size {
        return Err(format!(
            "{}: expected {size} grid rows, found {}",
            path.display(),
            result.len() / size
        ));
    }
    Ok(result)
}

pub fn read_block_array(path: &Path, size: usize) -> Result<Vec<i32>, String> {
    let input = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let mut result = Vec::with_capacity(size * size);
    let mut rows = 0;
    for line in input.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let tokens: Vec<_> = trimmed.split_whitespace().collect();
        if tokens.len() != size {
            return Err(format!(
                "{}: every block row must contain {size} values",
                path.display()
            ));
        }
        for token in tokens {
            result.push(token.parse().map_err(|_| {
                format!("{}: invalid block label: {token}", path.display())
            })?);
        }
        rows += 1;
    }
    if rows != size {
        return Err(format!(
            "{}: expected {size} block rows, found {rows}",
            path.display()
        ));
    }
    Ok(result)
}

pub fn format_grid(grid: &[i32], size: usize, pattern: bool) -> String {
    let mut output = String::new();
    for row in grid.chunks_exact(size) {
        for (column, &value) in row.iter().enumerate() {
            if column > 0 {
                output.push(' ');
            }
            if pattern {
                output.push(if value == 0 { '-' } else { 'X' });
            } else {
                output.push_str(&value.to_string());
            }
        }
        output.push('\n');
    }
    output
}

#[derive(Clone)]
pub struct XmlData {
    pub size: usize,
    pub hint: Vec<i32>,
    pub has_hint: bool,
    pub hidden: Vec<i32>,
    pub answer: Vec<i32>,
    pub problem: Vec<i32>,
    pub block_array: Option<Vec<i32>>,
    pub group_arrays: Vec<Vec<i32>>,
    pub seed: Option<Vec<i32>>,
    pub comment: Option<String>,
    pub vertical: bool,
    pub horizontal: bool,
    pub diagonal: bool,
    pub default_block: bool,
    pub difficult: i32,
}

impl XmlData {
    pub fn load(path: &Path) -> Result<Self, String> {
        let xml = fs::read_to_string(path).map_err(|error| error.to_string())?;
        Self::from_xml_str(&xml).map_err(|error| format!("{}: {error}", path.display()))
    }

    pub fn from_xml_str(xml: &str) -> Result<Self, String> {
        let problem_tag = start_tag(&xml, "problem")
            .ok_or_else(|| "root element must be <problem>".to_string())?;
        let size: usize = attribute(problem_tag, "size")
            .and_then(|value| value.parse().ok())
            .filter(|&value| (2..=25).contains(&value))
            .ok_or_else(|| "invalid problem size".to_string())?;
        let cells = size * size;
        let question_tag = start_tag(&xml, "question");
        let difficult = question_tag
            .and_then(|tag| attribute(tag, "difficult"))
            .and_then(|value| value.parse().ok())
            .unwrap_or(-1);
        let constraint = start_tag(&xml, "constraint")
            .ok_or_else(|| "missing <constraint>".to_string())?;
        let vertical =
            attribute(constraint, "vertical").is_none_or(|value| value == "on");
        let horizontal =
            attribute(constraint, "horizonal").is_none_or(|value| value == "on");
        let diagonal = attribute(constraint, "diagonal") == Some("on");
        let default_block = attribute(constraint, "default-block") == Some("on");
        let group_arrays = element_texts(&xml, "group")
            .into_iter()
            .map(|value| parse_int_array(value, cells))
            .collect::<Result<Vec<_>, _>>()?;
        let block_array = (!default_block)
            .then(|| group_arrays.first().cloned())
            .flatten();
        let hint_text = element_text(&xml, "hint");
        let seed = element_text(&xml, "seed")
            .map(|value| parse_int_array(value, cells))
            .transpose()?;
        let comment = element_text(&xml, "comment")
            .map(decode_xml_text)
            .transpose()?;
        Ok(Self {
            size,
            problem: parse_optional_array(&xml, "question", cells)?,
            hint: hint_text
                .map(|value| parse_int_array(value, cells))
                .transpose()?
                .unwrap_or_else(|| vec![0; cells])
                .into_iter()
                .map(|value| i32::from(value != 0))
                .collect(),
            has_hint: hint_text.is_some(),
            hidden: parse_optional_array(&xml, "hidden", cells)?,
            answer: parse_optional_array(&xml, "answer", cells)?,
            block_array,
            group_arrays,
            seed,
            comment,
            vertical,
            horizontal,
            diagonal,
            default_block,
            difficult,
        })
    }

    pub fn to_xml_string(&self) -> Result<String, String> {
        let cells = self.size * self.size;
        for (name, values) in [
            ("problem", &self.problem),
            ("answer", &self.answer),
            ("hidden", &self.hidden),
        ] {
            if values.len() != cells {
                return Err(format!("{name} must contain {cells} cells"));
            }
        }
        if self.hint.len() != cells {
            return Err(format!("hint must contain {cells} cells"));
        }
        if !self.default_block
            && self
                .block_array
                .as_ref()
                .is_none_or(|values| values.len() != cells)
        {
            return Err(format!("block array must contain {cells} cells"));
        }
        let mut xml = String::new();
        xml.push_str("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xml.push_str(&format!(
            "<problem size=\"{}\" name=\"Number Place\" author=\"Number Place Generator\">",
            self.size
        ));
        xml.push_str(&format!(
            "<question difficult=\"{}\">{}</question>",
            self.difficult,
            join(&self.problem)
        ));
        xml.push_str(&format!(
            "<constraint default-block=\"{}\" diagonal=\"{}\">",
            if self.default_block { "on" } else { "off" },
            if self.diagonal { "on" } else { "off" }
        ));
        if !self.vertical || !self.horizontal {
            xml.truncate(xml.len() - 1);
            if !self.vertical {
                xml.push_str(" vertical=\"off\"");
            }
            if !self.horizontal {
                xml.push_str(" horizonal=\"off\"");
            }
            xml.push('>');
        }
        if !self.default_block {
            xml.push_str(&format!(
                "<group block=\"on\">{}</group>",
                join(self.block_array.as_ref().expect("validated block array"))
            ));
        }
        xml.push_str("</constraint>");
        xml.push_str(&format!("<answer>{}</answer>", join(&self.answer)));
        xml.push_str(&format!("<hint>{}</hint>", join(&self.hint)));
        xml.push_str(&format!("<hidden>{}</hidden>", join(&self.hidden)));
        if let Some(comment) = &self.comment {
            xml.push_str(&format!("<comment>{}</comment>", escape_xml_text(comment)));
        }
        xml.push_str("</problem>\n");
        Ok(xml)
    }

    pub fn save(&self, path: &Path) -> Result<(), String> {
        fs::write(path, self.to_xml_string()?).map_err(|error| error.to_string())
    }
}

fn start_tag<'a>(xml: &'a str, name: &str) -> Option<&'a str> {
    let start = xml.find(&format!("<{name}"))?;
    let end = xml[start..].find('>')? + start + 1;
    Some(&xml[start..end])
}

fn attribute<'a>(tag: &'a str, name: &str) -> Option<&'a str> {
    let marker = format!("{name}=\"");
    let start = tag.find(&marker)? + marker.len();
    let end = tag[start..].find('"')? + start;
    Some(&tag[start..end])
}

fn element_text<'a>(xml: &'a str, name: &str) -> Option<&'a str> {
    let start = xml.find(&format!("<{name}"))?;
    let content_start = xml[start..].find('>')? + start + 1;
    let content_end = xml[content_start..].find(&format!("</{name}>"))? + content_start;
    Some(&xml[content_start..content_end])
}

fn element_texts<'a>(xml: &'a str, name: &str) -> Vec<&'a str> {
    let mut values = Vec::new();
    let mut remaining = xml;
    let opening = format!("<{name}");
    let closing = format!("</{name}>");
    while let Some(start) = remaining.find(&opening) {
        let content_start = match remaining[start..].find('>') {
            Some(offset) => start + offset + 1,
            None => break,
        };
        let content_end = match remaining[content_start..].find(&closing) {
            Some(offset) => content_start + offset,
            None => break,
        };
        values.push(&remaining[content_start..content_end]);
        remaining = &remaining[content_end + closing.len()..];
    }
    values
}

fn parse_optional_array(xml: &str, name: &str, length: usize) -> Result<Vec<i32>, String> {
    match element_text(xml, name) {
        Some(value) => parse_int_array(value, length),
        None => Ok(vec![0; length]),
    }
}

fn parse_int_array(value: &str, length: usize) -> Result<Vec<i32>, String> {
    let mut result = vec![0; length];
    for (index, token) in value.split_whitespace().take(length).enumerate() {
        result[index] = token
            .parse()
            .map_err(|_| format!("invalid integer in XML: {token}"))?;
    }
    Ok(result)
}

fn join(values: &[i32]) -> String {
    values
        .iter()
        .map(i32::to_string)
        .collect::<Vec<_>>()
        .join(" ")
}

fn escape_xml_text(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

fn decode_xml_text(value: &str) -> Result<String, String> {
    let mut result = String::with_capacity(value.len());
    let mut remaining = value;
    while let Some(offset) = remaining.find('&') {
        result.push_str(&remaining[..offset]);
        remaining = &remaining[offset..];
        let end = remaining
            .find(';')
            .ok_or_else(|| "unterminated XML entity".to_string())?;
        let entity = &remaining[1..end];
        match entity {
            "amp" => result.push('&'),
            "lt" => result.push('<'),
            "gt" => result.push('>'),
            "quot" => result.push('"'),
            "apos" => result.push('\''),
            value if value.starts_with("#x") => {
                let code = u32::from_str_radix(&value[2..], 16)
                    .map_err(|_| format!("invalid XML entity: &{entity};"))?;
                result.push(
                    char::from_u32(code)
                        .ok_or_else(|| format!("invalid XML entity: &{entity};"))?,
                );
            }
            value if value.starts_with('#') => {
                let code = value[1..]
                    .parse()
                    .map_err(|_| format!("invalid XML entity: &{entity};"))?;
                result.push(
                    char::from_u32(code)
                        .ok_or_else(|| format!("invalid XML entity: &{entity};"))?,
                );
            }
            _ => return Err(format!("unknown XML entity: &{entity};")),
        }
        remaining = &remaining[end + 1..];
    }
    result.push_str(remaining);
    Ok(result)
}
