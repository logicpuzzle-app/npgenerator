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
import java.util.Arrays;
import java.util.Stack;


public class BlockConstraint {
	private int numSize ; 
	// 以下のことを前提
	// [0 ... n) は縦列
	// [n ... n+n) は横列
	// それ以降はブロック
	private final ArrayList<Integer[]> block;
	private final int[][] primitiveBlock;
	
	// セルが属しているゾーン　[cell_idx][...]
	private final int[][] blockWhereCellBelong ;
	private final int[][] blockPositionWhereCellBelong;
	
	// ２つのブロック両方に含まれるセルの配列　　[block_idx1][block_idx2]
	private final Integer[][][] blockSetIntersection ;

	private final ArrayList<Pair<Integer, Integer> > intesectionList;
	private final int[][] intersectionDetails;

	ArrayList<Pair<Integer, Integer> > getIntersetionList(){
		return intesectionList;
	}
	
	public BlockConstraint(ArrayList<Integer[]> block , int n){
		this.block = block;
		this.numSize = n ;
		//setupBlock
		n = n*n;
		blockWhereCellBelong = new int[n][];
		int[] hist = new int[n]; 
		// 各配列長をあらかじめ求めておく
		for(Integer[] seq : block) for(Integer val : seq){
				hist[val]++;
			}
		for(int i=0;i<n;i++){
			blockWhereCellBelong[i] = new int[hist[i]]; 
		}
		blockPositionWhereCellBelong = new int[n][];
		for(int i=0;i<n;i++){
			blockPositionWhereCellBelong[i] = new int[hist[i]];
		}
		int[] cursor = new int[n];
		for(int i=0;i<block.size();i++)
		{
			for(int j=0;j<block.get(i).length;j++)
			{
				int x = block.get(i)[j];
				int position = cursor[x]++;
				blockWhereCellBelong[x][position] = i ;
				blockPositionWhereCellBelong[x][position] = j;
			}
		}

		// blockSetIntesection initialize
		for(int i=0;i<block.size();i++) Arrays.sort(block.get(i));
		primitiveBlock = new int[block.size()][];
		for(int i=0;i<block.size();i++){
			Integer[] source = block.get(i);
			int[] cells = new int[source.length];
			for(int j=0;j<source.length;j++) cells[j] = source[j];
			primitiveBlock[i] = cells;
		}
		// Sorting changes the position within a block, so rebuild the position map.
		Arrays.fill(cursor, 0);
		for(int i=0;i<primitiveBlock.length;i++){
			for(int j=0;j<primitiveBlock[i].length;j++){
				int cell = primitiveBlock[i][j];
				int membership = cursor[cell]++;
				blockPositionWhereCellBelong[cell][membership] = j;
			}
		}
		blockSetIntersection = new Integer[block.size()][block.size()][];
		intesectionList = new ArrayList<Pair<Integer,Integer> >();
		ArrayList<int[]> details = new ArrayList<int[]>();
		for(int i=0;i<block.size();i++)
			for(int j=i+1;j<block.size();j++)
			{
				int[] first = primitiveBlock[i];
				int[] second = primitiveBlock[j];
				Integer[] intersection = new Integer[Math.min(first.length, second.length)];
				int firstIndex = 0;
				int secondIndex = 0;
				int count = 0;
				int firstMask = 0;
				int secondMask = 0;
				while(firstIndex < first.length && secondIndex < second.length){
					int firstCell = first[firstIndex];
					int secondCell = second[secondIndex];
					if(firstCell < secondCell){
						firstIndex++;
					}else if(firstCell > secondCell){
						secondIndex++;
					}else{
						intersection[count++] = firstCell;
						firstMask |= 1 << firstIndex;
						secondMask |= 1 << secondIndex;
						firstIndex++;
						secondIndex++;
					}
				}
				blockSetIntersection[i][j] = Arrays.copyOf(intersection, count);
				if(blockSetIntersection[i][j].length >= 2){
					Pair<Integer,Integer> pair = new Pair<Integer,Integer>(i,j);
					intesectionList.add(pair);
					details.add(new int[] {i, j, firstMask, secondMask});
				}
			}
		intersectionDetails = details.toArray(new int[0][]);
	}
	public int getBlockSize() { return block.size(); }
	public ArrayList<Integer[]> getBlock(){
		return block;
	}
	public int[] getPrimitiveBlock(int block_idx){
		return primitiveBlock[block_idx];
	}
	public int[][] getBlockWhereCellBelong(){
		return blockWhereCellBelong;
	}
	public int[] getBlockWhereCellBelong(int cell_idx){
		return blockWhereCellBelong[cell_idx];
	}
	public int[] getBlockPositionWhereCellBelong(int cell_idx){
		return blockPositionWhereCellBelong[cell_idx];
	}
	public int[][] getIntersectionDetails(){
		return intersectionDetails;
	}
	public Integer[][][] getBlockSetIntesection(){
		return blockSetIntersection;
	}
	public Integer[] getBlockArray(){
		Integer[] array  = new Integer[numSize*numSize] ;
		for(int i=numSize+numSize,lbl=1;i<block.size();i++,lbl++){
			for(int cell_idx : block.get(i)){
				array[cell_idx] = lbl ; 
			}
		}
		return array;
	}
	/** 
	 * 線分データから、ブロックデータに変換
	 * @param numSize サイズ
	 * @param vLine 縦線
	 * @param hLine　横線
	 * @return
	 */
	public static int[] getBlockArrayFromLine(int numSize , boolean[][] vLine , boolean[][] hLine) {
		int[] array  = new int[numSize*numSize] ;
		Arrays.fill(array, -1);
		final int dx[] = {1,0,-1,0};
		final int dy[] = {0,-1,0,1};
		int color = 0;
		for(int i=0;i<array.length;i++) if(array[i]==-1){
			Stack<Integer> st = new Stack<Integer>();
			st.push(i); color++;
			int num = 0;
			while(!st.empty()){
				int now = st.pop();
				if(array[now]!=-1) continue;
				array[now] = color;
				num++;
				int row = now/numSize;
				int col = now%numSize;
				for(int j=0;j<4;j++){
					int nrow = row + dy[j];
					int ncol = col + dx[j];
					int nidx = nrow*numSize+ncol;
					if(0<=nrow && nrow < numSize && 0 <= ncol && ncol < numSize && array[nidx]==-1){
						if(dy[j]==0){
							if(vLine[row][Math.min(col, ncol)]) continue;
						}else{
							if(hLine[Math.min(row, nrow)][col]) continue;							
						}
						st.push(nidx);
					}
				}
			}
			if(num != numSize) return null;
		}
		return array;
	}
	//ArrayList<Pair<>>
}
