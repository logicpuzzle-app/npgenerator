/*
 *    Copyright(C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 *    All rights reserved.
 *    Java 17 reference rewrite derived from NPGenerator V2.0.2.
 *
 *    Number Place Generator Version 2.0
 *        Director:   Hirofumi Fujiwara
 *        Puzzler:    Naoki Inaba
 *        Programmer: Masaya Kiwada
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *    
 */

package jp.gr.puzzle.npgen2007;

import java.util.ArrayList;

public class ProblemContent {
	private int  num_size ;
	private int[] cell ;
	private ArrayList<Integer[]> block;

	public int[] getCell(){ 
		return cell; 
	}
	public void setCell(int[] cell){
		this.cell = cell; 
	}
	public int getNumberSize(){ 
		return num_size; 
	}
	public void setNumberSize(int num_size){
		this.num_size = num_size;
	}
	public ArrayList<Integer[]> getBlock(){ 
		return block; 
	}
	public void setBlock(ArrayList<Integer[]> block){
		this.block = block;
	}
	public int getBlockNum(){ 
		return block.size(); 
	}
	public ProblemContent(int size , int[] cell){
		this(size , cell , new ArrayList<Integer[]>());
	}
	public ProblemContent(int size , int[] cell , ArrayList<Integer[]> block)
	{
		this.num_size = size;
		this.cell = cell;
		this.block = block;
	}

	/**
	 * 縦横のブロックを一通りセットする
	 *
	 */
	public void addBlockVerticalAndHorizonal(){
		Utility.addBlockVerticalAndHorizonal(getBlock(), num_size);
	}
	/**
	 * w * h のブロック制約条件を付加する
	 * @param w　ブロックの幅
	 * @param h　ブロックの高さ
	 * w * h = num_size でなければならない
	 */
	public void addBlockRectangle(int w , int h)
	{
		Utility.addBlockRectangle(w, h, getBlock(), num_size);
	}
	/**
	 * テキストファイルから問題を読み込む
	 * @param filename ファイル名
	 */
	public void loadText(String filename) throws java.io.IOException {
		ProblemContent c = ProblemBuilder.loadText(filename);
		this.block = c.block;
		this.cell  = c.cell ;
		this.num_size = c.num_size;
	}
	public void addBlock(Integer[] new_block){
		block.add(new_block);
	}
}
