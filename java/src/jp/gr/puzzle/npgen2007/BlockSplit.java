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

public class BlockSplit {
	private int width , height ; 
	private final static int[] dx = new int[]{0,1,0,-1};
	private final static int[] dy = new int[]{1,0,-1,0};
	boolean[][] vWall , hWall;
	private final JavaRandom random;
	private int toindex(int x , int y , int w , int h){
		return y*w+x;
	}
	public BlockSplit(int width , int height) {
		this(width, height, new JavaRandom(0));
	}
	public BlockSplit(int width , int height, JavaRandom random) {
		this.width = width;
		this.height = height;
		this.random = random;
	}
	private void generateMaze(int w ,int h){
		vWall = new boolean[w+1][h+1];
		hWall = new boolean[w+1][h+1];
		DisjointSet group = new DisjointSet(w * h);
		ArrayList<Pair<Integer,Integer> > edge = new ArrayList<Pair<Integer,Integer> >();
		// 枝を網羅する
		for(int i=0;i<w;i++)
			for(int j=0;j<h;j++)
			{
				vWall[i][j]=true;
				hWall[i][j]=true;
				int p = toindex(i, j, w, h);
				if(i+1<w){
					int ph = toindex(i+1, j, w, h);
					edge.add(new Pair<Integer,Integer>(p,ph));
				}
				if(j+1<h){
					int pv = toindex(i, j+1, w, h);
					edge.add(new Pair<Integer,Integer>(p,pv));
				}
			}
		random.shuffle(edge);
		int remain = w * h - 1;
		for(Pair<Integer,Integer> p : edge)
		{
			if(remain <= 0) break;
			int p1 = p.getFirst();
			int p2 = p.getSecond();
			int p1x = p1%w, p1y = p1/w;
			int p2x = p2%w, p2y = p2/w;
			if(group.isSameGroup(p1, p2)==false){// 閉路ができない
				group.union(p1, p2);
				if(p1x == p2x){
					vWall[p1x][p1y] = false;
				}else if(p1y == p2y){
					hWall[p1x][p1y] = false;					
				}
				remain--;
			}
		}
		/*// debug
		System.out.println(remain);
		for(int j=0;j<h;j++){
			for(int i=0;i<w;i++)
			{
				System.out.print(".");
				if(hWall[i][j])
					System.out.print("X");
				else
					System.out.print(".");
			}
			System.out.println();
			for(int i=0;i<w;i++){
				if(vWall[i][j])
					System.out.print("X");
				else
					System.out.print(".");
				System.out.print("X");
			}			
			System.out.println();
		}*/
	}
	private boolean isMovable(int px , int py , int qx , int qy)
	{
		if(px == qx){
			if(py > qy) return isMovable(px , qy,  qx , py);
			if(py+1!=qy) return false;
			if((py & 1)==0) return true;
			return vWall[px/2][py/2]==false;
		}else if(py == qy){
			if(px > qx) return isMovable(qx , py,  px , qy);
			if(px+1!=qx) return false;
			if((px & 1)==0) return true;
			return hWall[px/2][py/2]==false;
		}else return false;
	}
	private int[] walkMaze(){
		//int px=width-1 , py=height-1 , dir = 3;
		int px=0 , py=0 , dir = 0;
		px = random.nextInt(width /2)*2;
		py = random.nextInt(height/2)*2;
		int[] result  = new int[width * height];
		Arrays.fill(result , -1);
		int id = 0 , cnt = 0;
		int w = width , h = height;
		int numsize = width;
		boolean yet = true;
		while(yet)
		{
			yet = false;
			result[toindex(px, py, w, h)] = id ;
			cnt ++ ;
			if(cnt == numsize){
				id ++;
				cnt = 0;
			}
			for(int i=3;i<7;i++){
				int fx = px + dx[(i+dir)%4];
				int fy = py + dy[(i+dir)%4];
				if(width%2!=0 && height%2!=0 && fx==width-1 && fy==height-1 && cnt != 0
						&& result[toindex(fx, fx, w, h)] == -1){
					result[toindex(fx, fx, w, h)] = id ;
					cnt ++ ;
					if(cnt == numsize){
						id ++;
						cnt = 0;
					}
				}
				if(0<=fx && fx < w && 0<=fy && fy < h && 
						isMovable(px, py, fx, fy) && 
						result[toindex(fx, fy, w, h)]==-1){
					yet = true;
					dir = (i+dir)%4;
					px = fx;
					py = fy;
					break;
				}
			}
		}
		/*
		for(int i=0;i<w;i++){
			for(int j=0;j<h;j++)
			{
				System.out.printf("%3d" , result[toindex(i, j, w, h)]);
			}
			System.out.println("");
		}
		*/
		return result;
	}
	public int[] splitBlock()
	{
		// 迷路を作成 -> 2倍にする -> 右手法でたどりながらラベルを付ける 
		int w = width/2, h = height/2;
		generateMaze(w,h); 
		if(width % 2 != 0){
			for(int i=0;i<h;i++){
				vWall[w][i] = true;
				hWall[w-1][i] = false;
			}
		}
		if(height % 2 != 0){
			for(int i=0;i<w;i++){
				vWall[i][h-1] = false;
				hWall[i][h] = true;
			}
		}
		return walkMaze();
	}
	public static void main(String[] args) {
		BlockSplit bs = new BlockSplit(500,500);
		bs.splitBlock();
	}
}


class DisjointSet
{
	private int[] array;
	public DisjointSet(int n)
	{
		array = new int[n];
		for(int i=0;i<n;i++) array[i]=i;
	}
	public int find(int a){
		if(array[a]==a) return a;
		return array[a]=find(array[a]);
	}
	public void union(int a , int b){
		int pa , pb;
		pa = find(a);
		pb = find(b);
		array[pa]=pb;
	}
	public boolean isSameGroup(int a ,int b){
		return find(a) == find(b);
	}
}
