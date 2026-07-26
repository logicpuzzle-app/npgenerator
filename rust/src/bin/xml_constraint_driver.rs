/*
 * Verification driver for Java-compatible XML constraint ordering.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

use std::env;
use std::fs;
use std::process::ExitCode;

fn main() -> ExitCode {
    let mut args = env::args().skip(1);
    let Some(path) = args.next() else {
        eprintln!("usage: xml_constraint_driver <problem.xml>");
        return ExitCode::from(2);
    };
    if args.next().is_some() {
        eprintln!("usage: xml_constraint_driver <problem.xml>");
        return ExitCode::from(2);
    }
    match fs::read_to_string(&path)
        .map_err(|error| error.to_string())
        .and_then(|xml| npgen::xml_constraint_dump(&xml))
    {
        Ok(output) => {
            print!("{output}");
            ExitCode::SUCCESS
        }
        Err(error) => {
            eprintln!("input error: {error}");
            ExitCode::from(2)
        }
    }
}
