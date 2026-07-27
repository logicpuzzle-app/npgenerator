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
import java.util.HashMap;
import java.util.Scanner;

/**
 * よく使う便利な関数クラス群
 * @author kiwada
 *
 */
public class Utility {
	private Utility(){;}
	public static <T> String toStringFromArray(T[] array){
		StringBuffer buf = new StringBuffer();
		for(int i=0;i<array.length;i++){
			if(i!=0)
				buf.append(' ');
			buf.append(array[i].toString());
		}
		return buf.toString();
	}
	public static boolean isAllZero(int [] array){
		for(int x : array){
			if(x != 0) return false;
		}
		return true;
	}
	public static String toStringFromArray(int[] array){
		StringBuffer buf = new StringBuffer();
		for(int i=0;i<array.length;i++){
			if(i!=0)
				buf.append(' ');
			buf.append(array[i]);
		}
		return buf.toString();
	}
	public static String toStringFromArray(boolean[] array){
		StringBuffer buf = new StringBuffer();
		for(int i=0;i<array.length;i++){
			if(i!=0)
				buf.append(' ');
			buf.append(array[i]?'1':'0');
		}
		return buf.toString();
	}
	
	/**
	 * 文字列をbool[]に変換する
	 * あまりの部分は０で埋められ、はみ出た部分は切り捨てられる
	 * @param str 変換する文字列
	 * @param size 配列の長さ
	 * @return 変換された配列
	 */
	public static boolean[] toBooleanArray(String str , int size )
	{
		if(str == null){
			return new boolean[size];
		}
		Scanner scan = new Scanner(str);
		boolean[] val = new boolean[size];
		int index = 0;
		while(scan.hasNext()){
			if(index == size) break;
			val[index++] = scan.nextInt()!=0?true:false;
		}
		return val;
	}
	/**
	 * 文字列をint[]に変換する
	 * あまりの部分は０で埋められ、はみ出た部分は切り捨てられる
	 * @param str 変換する文字列
	 * @param size 配列の長さ
	 * @return 変換された配列
	 */
	public static int[] toIntArray(String str , int size )
	{
		if(str == null){
			return new int[size];
		}
		Scanner scan = new Scanner(str);
		int[] val = new int[size];
		int index = 0;
		while(scan.hasNext()){
			if(index == size) break;
			val[index++] = scan.nextInt();
		}
		return val;
	}
	/**
	 * 文字列をInteger[]に変換する
	 * あまりの部分は０で埋められ、はみ出た部分は切り捨てられる
	 * @param str 変換する文字列
	 * @param size 配列の長さ
	 * @return 変換された配列
	 */
	public static Integer[] toIntegerArray(String str , int size )
	{
		int[] intarray = toIntArray(str, size);
		Integer[] val = new Integer[size];
		for(int i=0;i<size;i++)
			val[i] = intarray[i];
		return val;
	}
	
	public static int[] integer2int( Integer[] from ) {
		int[] to = new int[from.length];
		for( int i=0; i<from.length; ++i )
			to[i] = from[i];
		return to;
	}
	public static Integer[] int2integer( int[] from ) {
		Integer[] to = new Integer[from.length];
		for( int i=0; i<from.length; ++i )
			to[i] = from[i];
		return to;
	}
	
	public static boolean[] int2boolean( int[] from ) {
		boolean[] to = new boolean[from.length];
		for( int i=0; i<from.length; ++i )
			to[i] = from[i]!=0;
		return to;
	}
	public static int[] boolean2int( boolean[] from ) {
		int[] to = new int[from.length];
		for( int i=0; i<from.length; ++i )
			to[i] = from[i]? 1 : 0;
		return to;
	}
	
	public static void addBlockVerticalAndHorizonal(ArrayList<Integer[]> out , int num_size){
		addBlockVertical(out, num_size);
		addBlockHorizontal(out, num_size);
	}	
	/**
	 * 標準のブロックを作る
	 */
	public static ArrayList<Integer[]> makeNormalBlock( int n, int w, int h ) {
		ArrayList<Integer[]> b = new ArrayList<Integer[]>();
		Utility.addBlockVertical(b, n);
		Utility.addBlockHorizontal(b, n);
		Utility.addBlockRectangle( w, h, b, n);
		
		return b;
	}
	/**
	 * 縦横のブロックを一通りセットする
	 *
	 */
	public static void addBlockVertical(ArrayList<Integer[]> out , int num_size){
		for(int i=0;i<num_size;i++){
			Integer [] vertical = new Integer[num_size];
			for(int j=0;j<num_size;j++){
				vertical[j] = j*num_size + i ;
			}
			out.add(vertical);
		}
	}	
	/**
	 * 縦横のブロックを一通りセットする
	 *
	 */
	public static void addBlockHorizontal(ArrayList<Integer[]> out , int num_size){
		for(int i=0;i<num_size;i++){
			Integer [] horizon  = new Integer[num_size];
			for(int j=0;j<num_size;j++){
				horizon[j]  = i*num_size + j ;
			}
			out.add(horizon);
		}
	}
	/**
	 * w * h のブロック制約条件を付加する
	 * @param w　ブロックの幅
	 * @param h　ブロックの高さ
	 * w * h = num_size でなければならない
	 */
	public static void addBlockRectangle(int w , int h , ArrayList<Integer[]> out , int num_size)
	{
		for(int i=0;i<num_size;i+=w){
			for(int j=0;j<num_size;j+=h){
				Integer [] rect = new Integer[num_size];
				int index = 0;
				for(int k=0;k<w;k++){
					for(int l=0;l<h;l++){
						rect[index++] = toIndex(j+l,i+k,num_size); 
					}
				}
				out.add(rect);
			}
		}
	}	
	private static int toIndex(int row , int col , int n){
		return row * n + col ;
	}
	/**
	 * 対角線
	 */
	public static void addBlockDiagonal(ArrayList<Integer[]> out , int num_size)
	{
		Integer[] dia = new Integer[num_size];
		for(int i=0;i<num_size;i++){ 
			dia[i] = toIndex(i, i, num_size);
		}
		out.add(dia);
		dia = new Integer[num_size];
		for(int i=0;i<num_size;i++){ 
			dia[i] = toIndex(num_size-1-i, i, num_size);
		}
		out.add(dia);
	}
	public static boolean addBlockByArray(Integer [] array , ArrayList<Integer[]> out , int num_size)
	{
		HashMap<Integer, Integer> toindex = new HashMap<Integer,Integer>();
		int index = 1;
		for(int i=0;i<array.length;i++)
			if(array[i]!=0)
			{
				if(toindex.containsKey(array[i])==false)
				{
					toindex.put(array[i], index);
					array[i] = index++;
				}else array[i] = toindex.get(array[i]);
			}
		Integer[][] block = new Integer[index-1][num_size];
		int[] cursol = new int[num_size];
		for(int i=0;i<array.length;i++)
			if(array[i]!=0){
				block[array[i]-1][cursol[array[i]-1]++] = i ;
			}
		for(Integer[] seq : block) out.add(seq);
		return true;
	}
	/**
	 * Load BlockConstraint from NumberPlaceFile
	 * @param npFile Number Place File
	 * @return maked a BlockConstraint object from npFile
	 */
	public static BlockConstraint makeBlockConstraint(NumberPlaceFile npFile) {
		int size     = npFile.getNumSize();
		boolean isDiagonal = npFile.isDiagonal();
		boolean default_block = npFile.isDefaultBlock();
		
		ArrayList<Integer[]> blockarraylist = new ArrayList<Integer[]>();
		if(npFile.isVertical()) {
			Utility.addBlockVertical(blockarraylist, size);
		}
		if(npFile.isHorizontal()) {
			Utility.addBlockHorizontal(blockarraylist, size);
		}
		if(default_block ) {
	    	int sq = Utility.sqrt(size) ;
	    	Utility.addBlockRectangle(sq, sq, blockarraylist, size);
		}
		java.util.List<int[]> groups = npFile.getGroupArrays();
		if(groups.isEmpty() && !default_block && npFile.getBlockArray() != null) {
			groups = java.util.List.of(npFile.getBlockArray());
		}
		for(int[] group : groups) {
			Utility.addBlockByArray(
					Utility.int2integer(group), blockarraylist, size);
		}
		if( isDiagonal ) {
			Utility.addBlockDiagonal( blockarraylist, size);
		}
		
		return new BlockConstraint( blockarraylist , size );
	}
	
	/**
	 * 整数平方根を求める
	 * @param x
	 * @return xの整数平方根（小数点以下切り捨て）
	 */
	public static int sqrt(int x){
		return (int)(Math.sqrt(x)+1e-10);
	}
	
	public static void printGrid(int[] grid , int size){
		for(int i=0;i<size;i++){
			for(int j=0;j<size;j++){
				System.out.print(" " + grid[i*size+j]);
			}
			System.out.println();
		}
	}
	/**
	 * 多次元配列のディープコピーを返す
	 * @param array
	 * @return コピー
	 */
	public static int[][] dup(int[][] array){
		int[][] ret = new int[array.length][];
		for(int i=0;i<array.length;i++){
			int[] seq = new int[array[i].length];
			System.arraycopy(array[i], 0, seq, 0, seq.length);
			ret[i] = seq;
		}
		return ret;
	}
	public static boolean[][] dup(boolean[][] array){
		boolean[][] ret = new boolean[array.length][];
		for(int i=0;i<array.length;i++){
			boolean[] seq = new boolean[array[i].length];
			System.arraycopy(array[i], 0, seq, 0, seq.length);
			ret[i] = seq;
		}
		return ret;
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println(Bit.ntz(1<<6));
		System.out.println(Bit.ntz(1<<0));
		System.out.println(Bit.ntz(1<<25));
		System.out.println(Utility.toStringFromArray(new int[]{3,5,3,2}));
	}

}

/**
 * @see "Hacker's delight"
 */
class Bit{
	final public static int Right1BitOff(int x){
		return x&(x-1);
	}
	final public static int getRight1Bit(int x){
		return x&(-x);
	}
	final public static boolean is2(int x){
		return (x&(x-1))==0;
	}
	final public static int getNumberOf1Bit(int x){
		int n = 0;
		while(x!=0){
			x = Right1BitOff(x);
			n++;
		}
		return n;
	}
	final public static int ntz(int x){
		int n=1;
		if((x & 0xFFFF) == 0) { n= n+16; x >>= 16; }	
		if((x & 0x00FF) == 0) { n= n+ 8; x >>= 8; }	
		if((x & 0x000F) == 0) { n= n+ 4; x >>= 4; }	
		if((x & 0x0003) == 0) { n= n+ 2; x >>= 2; }	
		return n-(x&1);
	}
	final public static int ntz(long x){
		int n=1;
		if((x & 0xFFFFFFFF) == 0) { n= n+32; x >>= 32; }	
		if((x & 0x0000FFFF) == 0) { n= n+16; x >>= 16; }	
		if((x & 0x000000FF) == 0) { n= n+ 8; x >>= 8; }	
		if((x & 0x0000000F) == 0) { n= n+ 4; x >>= 4; }	
		if((x & 0x00000003) == 0) { n= n+ 2; x >>= 2; }	
		return (int)(n-(x&1));
	}
}
