# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Python rewrite of NPGenerator V2.0.2."""

from .api import Generated, SolveResult, generate, random_pattern, solve

__all__ = ["Generated", "SolveResult", "generate", "random_pattern", "solve"]
