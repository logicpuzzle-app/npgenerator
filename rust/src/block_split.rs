/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Rust rewrite derived from NPGenerator V2.0.2.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

use crate::random::JavaRandom;

struct DisjointSet {
    values: Vec<usize>,
}

impl DisjointSet {
    fn new(size: usize) -> Self {
        Self {
            values: (0..size).collect(),
        }
    }

    fn find(&mut self, value: usize) -> usize {
        if self.values[value] != value {
            self.values[value] = self.find(self.values[value]);
        }
        self.values[value]
    }

    fn union(&mut self, first: usize, second: usize) {
        let first = self.find(first);
        let second = self.find(second);
        self.values[first] = second;
    }

    fn same(&mut self, first: usize, second: usize) -> bool {
        self.find(first) == self.find(second)
    }
}

pub fn split_block(width: usize, height: usize, random: &mut JavaRandom) -> Vec<i32> {
    let maze_width = width / 2;
    let maze_height = height / 2;
    let mut vertical_wall = vec![vec![false; maze_height + 1]; maze_width + 1];
    let mut horizontal_wall = vec![vec![false; maze_height + 1]; maze_width + 1];
    let mut groups = DisjointSet::new(maze_width * maze_height);
    let mut edges = Vec::new();

    for x in 0..maze_width {
        for y in 0..maze_height {
            vertical_wall[x][y] = true;
            horizontal_wall[x][y] = true;
            let cell = y * maze_width + x;
            if x + 1 < maze_width {
                edges.push((cell, y * maze_width + x + 1));
            }
            if y + 1 < maze_height {
                edges.push((cell, (y + 1) * maze_width + x));
            }
        }
    }
    random.shuffle(&mut edges);
    let mut remain = maze_width * maze_height - 1;
    for &(first, second) in &edges {
        if remain == 0 {
            break;
        }
        if groups.same(first, second) {
            continue;
        }
        groups.union(first, second);
        let (first_x, first_y) = (first % maze_width, first / maze_width);
        let (second_x, second_y) = (second % maze_width, second / maze_width);
        if first_x == second_x {
            vertical_wall[first_x][first_y] = false;
        } else if first_y == second_y {
            horizontal_wall[first_x][first_y] = false;
        }
        remain -= 1;
    }

    if width % 2 != 0 {
        for y in 0..maze_height {
            vertical_wall[maze_width][y] = true;
            horizontal_wall[maze_width - 1][y] = false;
        }
    }
    if height % 2 != 0 {
        for x in 0..maze_width {
            vertical_wall[x][maze_height - 1] = false;
            horizontal_wall[x][maze_height] = true;
        }
    }

    walk_maze(
        width,
        height,
        &vertical_wall,
        &horizontal_wall,
        random,
    )
}

fn walk_maze(
    width: usize,
    height: usize,
    vertical_wall: &[Vec<bool>],
    horizontal_wall: &[Vec<bool>],
    random: &mut JavaRandom,
) -> Vec<i32> {
    const DX: [isize; 4] = [0, 1, 0, -1];
    const DY: [isize; 4] = [1, 0, -1, 0];
    let mut x = random.next_int((width / 2) as i32) as usize * 2;
    let mut y = random.next_int((height / 2) as i32) as usize * 2;
    let mut direction = 0;
    let mut result = vec![-1; width * height];
    let mut id = 0;
    let mut count = 0;
    let mut yet = true;
    while yet {
        yet = false;
        result[y * width + x] = id;
        count += 1;
        if count == width {
            id += 1;
            count = 0;
        }
        for step in 3..7 {
            let next_direction = (step + direction) % 4;
            let next_x = x as isize + DX[next_direction];
            let next_y = y as isize + DY[next_direction];
            if width % 2 != 0
                && height % 2 != 0
                && next_x == width as isize - 1
                && next_y == height as isize - 1
                && count != 0
                && result[next_x as usize * width + next_x as usize] == -1
            {
                result[next_x as usize * width + next_x as usize] = id;
                count += 1;
                if count == width {
                    id += 1;
                    count = 0;
                }
            }
            if next_x >= 0
                && next_x < width as isize
                && next_y >= 0
                && next_y < height as isize
                && is_movable(
                    x,
                    y,
                    next_x as usize,
                    next_y as usize,
                    vertical_wall,
                    horizontal_wall,
                )
                && result[next_y as usize * width + next_x as usize] == -1
            {
                yet = true;
                direction = next_direction;
                x = next_x as usize;
                y = next_y as usize;
                break;
            }
        }
    }
    result
}

fn is_movable(
    first_x: usize,
    first_y: usize,
    second_x: usize,
    second_y: usize,
    vertical_wall: &[Vec<bool>],
    horizontal_wall: &[Vec<bool>],
) -> bool {
    if first_x == second_x {
        if first_y > second_y {
            return is_movable(
                first_x,
                second_y,
                second_x,
                first_y,
                vertical_wall,
                horizontal_wall,
            );
        }
        if first_y + 1 != second_y {
            return false;
        }
        first_y % 2 == 0 || !vertical_wall[first_x / 2][first_y / 2]
    } else if first_y == second_y {
        if first_x > second_x {
            return is_movable(
                second_x,
                first_y,
                first_x,
                second_y,
                vertical_wall,
                horizontal_wall,
            );
        }
        if first_x + 1 != second_x {
            return false;
        }
        first_x % 2 == 0 || !horizontal_wall[first_x / 2][first_y / 2]
    } else {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::split_block;
    use crate::random::JavaRandom;

    #[test]
    fn size_six_matches_reference() {
        let mut random = JavaRandom::new(42);
        assert_eq!(
            split_block(6, 6, &mut random),
            vec![
                0, 0, 0, 0, 0, 5, 0, 2, 2, 5, 5, 5, 1, 2, 2, 5, 5, 4, 1, 2,
                2, 3, 3, 4, 1, 1, 3, 3, 3, 4, 1, 1, 3, 4, 4, 4,
            ]
        );
    }
}
