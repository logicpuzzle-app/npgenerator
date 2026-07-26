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

import java.util.Arrays;

public class Status 
{
	// セルに入りうる数字の個数
	private int numSize ;
	
	// セル　：  数字の入るパネル
	private int [] cell ;
	
	// 候補　その座標にその値は入るか　[idx][r]
	//private boolean[][] cand ;
	private int[] cand ;
	private int[] candCounts;

	// そのブロックに、その数は存在するか [block_idx][n]
	private int[] exist ;

	// そのブロック内の、その数が入りうるマスの個数 [block_idx][n]
	private int[] candCountOfBlock ;
	private int[] candPositions;
	private int blockStride;

	// 空きマスの数
	private int spaceCount;

	// ブロックによる制約
	private BlockConstraint block;

	private int candCount ;
	
	private Solver.KindOfAnswer kind ;
	
	UniqueMethod unique = new UniqueMethod();
	
	final public boolean isCand(int cell_idx , int n){
		return (cand[cell_idx] & (1<<(n-1))) != 0;
	}
	final public Solver.KindOfAnswer getKindOfAnswer(){
		return kind;
	}
	final public void setKindOfAnswer(Solver.KindOfAnswer kind){
		this.kind = kind;
	}
	final public void setUniqueMethod(UniqueMethod m){
		unique = m; 
	}
	final public boolean isInvalid(){
		return  kind == Solver.KindOfAnswer.NO_ANSWER ||
				kind == Solver.KindOfAnswer.IRREGULAR_PROBLEM;
	}
	// set get
	final public int[] getCell()     { return cell; }
	final public int getCell(int idx){ return cell[idx] ; }
	final public boolean isEmptyCell(int idx) { return cell[idx]==0; }
	final public int getCellSize(){ return cell.length; }
	final public int getSize() { return numSize ; }
	final public int getBlockNum() { return block.getBlockSize(); }
	final public int getCandCountOfCell(int cell_idx){
		return candCounts[cell_idx];
	}
	final public int getCandCount(){
		return candCount;
	}
	final public boolean isNoCandidate(int cell_idx){
		return cand[cell_idx]==0;
	}
	final public boolean isUniqueCandidate(int cell_idx){
		return !isNoCandidate(cell_idx) && (cand[cell_idx] & (cand[cell_idx] - 1)) == 0;
	}
	final public int getUniqueCandidate(int cell_idx){
		return Integer.numberOfTrailingZeros(cand[cell_idx])+1;
	}
	final public BlockConstraint getBlockConstraint(){
		return block;
	}
	final public int getCandCountOfBlock(int block_idx , int n){
		return candCountOfBlock[block_idx * blockStride + n];
	}
	final public int getCandidateMask(int cell_idx){ return cand[cell_idx]; }
	final public int getCandidatePositionMask(int block_idx, int n){
		return candPositions[block_idx * blockStride + n];
	}
	final public int[] getBlock(int block_idx){ return block.getPrimitiveBlock(block_idx); }
	final public boolean isExistNumberOnZone(int idx , int x){ return (exist[idx]&(1<<(x-1)))!=0; }
	final public int getSpaceCount(){ return spaceCount; }

	public boolean isVHBlock(int block_idx){
		return (block_idx < 2 * numSize) ;
	}
	/**
	 * セルの(ｎ+1)番目の候補の数を返す
	 * @param cell_idx
	 * @param n
	 * @return 番号
	 */
	public int getNthCandOfCell(int cell_idx , int n){
		int x = cand[cell_idx];
		while(n>0){
			x &= x - 1; n--;
		}
		if(x==0) return -1;
		return Integer.numberOfTrailingZeros(x) + 1;
	}

	public Status(int num , BlockConstraint block)
	{
		int n2 = num * num ;
		this.cell            = new int    [n2];
		this.block           = block;
		this.numSize         = num ;
		cand                 = new int    [n2] ;
		candCounts           = new int    [n2] ;
		blockStride          = num + 1;
		candCountOfBlock     = new int    [block.getBlockSize() * blockStride];
		candPositions        = new int    [block.getBlockSize() * blockStride];
		exist                = new int    [block.getBlockSize()];
		clear();
	}
	public Status(ProblemContent p)
	{		
		this(p.getNumberSize() , new BlockConstraint(p.getBlock() ,p.getNumberSize()));
	}
	//public static long copytime = 0 , count = 0; 
	public void copyStatusToThis(Status state)
	{
		//count ++ ; 
		//long pt = System.nanoTime();
		this.unique               = state.unique;
		this.block			      = state.block;
		this.kind                 = state.kind;
		this.numSize              = state.numSize;
		this.spaceCount           = state.spaceCount;
		this.candCount             = state.candCount;
		System.arraycopy(state.cell , 0, cell , 0, cell .length);
		System.arraycopy(state.cand , 0, cand , 0, cand .length);
		System.arraycopy(state.candCounts, 0, candCounts, 0, candCounts.length);
		System.arraycopy(state.exist, 0, exist, 0, exist.length);
		System.arraycopy(state.candCountOfBlock, 0, candCountOfBlock, 0, candCountOfBlock.length);
		System.arraycopy(state.candPositions, 0, candPositions, 0, candPositions.length);
		//cell = state.cell.clone();
		//cand = state.cand.clone();
		//exist = state.exist.clone();
		//copytime += System.nanoTime() - pt ;
		
	}
	public void clear(){
		kind = Solver.KindOfAnswer.NO_JUDGE;
		Arrays.fill(cell, 0);
		Arrays.fill(exist, 0);
		spaceCount = cell.length;
		candCount = numSize * numSize * numSize;
		int fullMask = (1<<numSize)-1;
		Arrays.fill(candCountOfBlock, numSize);
		Arrays.fill(candPositions, fullMask);
		for(int i=0;i<block.getBlockSize();i++){
			candCountOfBlock[i * blockStride] = 0;
			candPositions[i * blockStride] = 0;
		}
		Arrays.fill(cand, fullMask);
		Arrays.fill(candCounts, numSize);
	}
	/**
	 * セル盤面に不正があるかどうか調べる
	 * @return
	 */
	public boolean isValid(){
		for(int block_idx=0;block_idx<block.getBlockSize();block_idx++) {
			int used = 0 ;
			for(int x : block.getPrimitiveBlock(block_idx)){
				if((used & (1<<cell[x])) != 0) return false;
				used |= (1<<cell[x]);
			}
		}
		return true;
	}
	/*int GetNumberOfCandidateOfCell(int cell_idx){
		return candCountOfCell[cell_idx];
	}*/
	/**
	 * 解がない盤面かどうかの判定を行う
	 * @return 解なしならTrue それ以外ならFalse
	 */
	public boolean isNoAnswer(){
		if(kind == Solver.KindOfAnswer.NO_ANSWER) return true;
		if(kind == Solver.KindOfAnswer.IRREGULAR_PROBLEM) return true;
		if(true) return false;
		// TEST CODE
		boolean flg = false; 
		for(int i=0;i<cell.length;i++){
			if(cell[i] == 0 && isNoCandidate(i)){
				flg = true; break;
			}
		}
		if(flg){
			if(kind != Solver.KindOfAnswer.NO_ANSWER &&
			kind != Solver.KindOfAnswer.IRREGULAR_PROBLEM)
				System.out.println("STRANGE");
		}else {
			if(kind == Solver.KindOfAnswer.NO_ANSWER ||
				kind == Solver.KindOfAnswer.IRREGULAR_PROBLEM){
					System.out.println("STRANGE 2 : " + kind );
					//showDebugData();
			}
		}
		return flg;
	}
	Integer[] getBlockInterSection(int a , int b){
		if(a == b) return null;
		if(a > b) return getBlockInterSection(b , a);
		return block.getBlockSetIntesection()[a][b];
	}
	/**
	 * そのセルに一意に入る数字を求める
	 * @param cell_idx セル番号
	 * @return 一意に決まる場合はその数字、決まらない場合-1を返する
	 */
	int UniqueCandidateNumberOfCell(int cell_idx){
		// ゾーンの制約条件より、そのセルに入りうる数字が１つだけの場合

		if(isUniqueCandidate(cell_idx)) {
			return Integer.numberOfTrailingZeros(cand[cell_idx]) + 1 ;
		}
		// block内でその数字が入りうるのが一個所だけの場合
		for(int block_idx : block.getBlockWhereCellBelong(cell_idx))
		{
			//if(block_idx < num_size * 2) continue;
			for(int j=1;j<=numSize;j++){
				if(getCandCountOfBlock(block_idx, j)==1){
					if(isCand(cell_idx,j))
						return j;
				}
			}
		}
		return -1;
	}
	/**
	 * セルに数字を割り当てる
	 * @param cell_idx セル番号
	 * @param n 割り当てる数字
	 */
	boolean assignValue(int cell_idx , int n){
		if(n==0) return false;		
		if(!isEmptyCell(cell_idx)) {
			if( n != cell[cell_idx] ) {
				setKindOfAnswer(Solver.KindOfAnswer.NO_ANSWER); 			
			}
			return false ;
		}
		if(isCand(cell_idx,n)==false) { 
			setKindOfAnswer(Solver.KindOfAnswer.NO_ANSWER); 
			return false; 
		}
		if(isNoAnswer()) return false;
		cell[cell_idx] = n ;
		spaceCount--;
		for(int block_idx : block.getBlockWhereCellBelong(cell_idx))
		{
			if(isExistNumberOnZone(block_idx, n)){
				kind = Solver.KindOfAnswer.NO_ANSWER;				
			}else
				exist[block_idx] |= (1<<(n-1));
		}
		return true;
	}
			
	/**
	 * セルの候補から数字ｎを抹消する
	 * @param cell_idx　セル番号
	 * @param n　抹消する数字
	 * @return 抹消したならTrue 、すでにされていた（つまり変動がない）ならFalse
	 */
	boolean deleteCandidate(int cell_idx , int n)
	{
		if(n==0) return false;
		if(isNoAnswer()) return false;
		if(isCand(cell_idx,n)==false) return false;
		if(isNoCandidate(cell_idx)){
			kind = Solver.KindOfAnswer.NO_ANSWER;
			return false;
		}
		cand[cell_idx] &= ~(1<<(n-1)); 
		candCounts[cell_idx]--;
		candCount--;
		// そのセルが属しているゾーンからも数字ｎが入る候補数を減らす
		int[] memberships = block.getBlockWhereCellBelong(cell_idx);
		int[] positions = block.getBlockPositionWhereCellBelong(cell_idx);
		for (int i=0;i<memberships.length;i++)
		{
			int candidate_idx = memberships[i] * blockStride + n;
			--candCountOfBlock[candidate_idx] ;
			candPositions[candidate_idx] &= ~(1 << positions[i]);
			if(candCountOfBlock[candidate_idx]==0){
				setKindOfAnswer(Solver.KindOfAnswer.NO_ANSWER);
			}
		}
		return true;
	}
	void showBlock(){ 
		int index = 0;
		for(int block_idx=0;block_idx<block.getBlockSize();block_idx++)
		{
			System.out.println(index++);
			int[][] field = new int[numSize][numSize];
			for(int x : block.getPrimitiveBlock(block_idx)) field[x/numSize][x%numSize] = 1;
			for(int i=0;i<numSize;i++){
				for(int j=0;j<numSize;j++)
					System.out.print(" " + field[i][j]);
				System.out.println("");
			}
			System.out.println("");
		}
	}
	void showCandData(){
		for(int i=0;i<numSize;i++){
			for(int j=0;j<numSize;j++){
				System.out.print(" " + getCandCountOfCell(i*numSize+j) + " ");
				for(int k=1;k<=numSize;k++){
					if(isCand(i*numSize+j,k)){
						System.out.print(k);
					}else
						System.out.print('-');
				}
			}
			System.out.println("");	
		}
	}
	void showCell(){
		Utility.printGrid(cell, numSize);
	}
	public void showDebugData()
	{
		
		System.out.println("! "+getSpaceCount());
		
		for(int i=0;i<block.getBlockSize();i++){
			for(int j=1;j<=numSize;j++)
				System.out.print(" " + getCandCountOfBlock(i, j));
			System.out.println("");
		}
		showCandData();
		for(int i=0;i<cell.length;i++){
			System.out.print(" " + getCandCountOfCell(i));
		}
		System.out.println("");	

		for(int i=0;i<numSize;i++){
			for(int j=0;j<numSize;j++){
				System.out.print(" " + cell[i*numSize+j]);
			}
			System.out.println("");
		}			
	}
}
