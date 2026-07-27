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
import java.util.HashSet;
import java.util.TreeSet;

public class Evaluator {
	public static boolean debug = false;
	static int preNumber = -1;
	static double historyCoef = 2.0;

	static final int BURIED_CELL_POINT  = 1;
	static final int DELETED_SAME_BLOCK = 2;
	static final int DELETED_SAME_LINE  = 3;
	static final double UNIQUE_BLOCK = 1.0;
	static final double UNIQUE_LINE  = 1.5;
	static final double UNIQUE_CELL  = 2.0;	
	static final double LOCALIZATION_LINE  = 1;
	static final double LOCALIZATION_BLOCK = 1;
	static final double NAKED_PAIR_LINE  = 1;
	static final double NAKED_PAIR_BLOCK  = 1;
	static final double HIDDEN_PAIR_LINE  = 1;
	static final double HIDDEN_PAIR_BLOCK  = 1;
	static final double NAKED_TRIPLE_LINE  = 1;
	static final double NAKED_TRIPLE_BLOCK  = 1;
	static final double HIDDEN_TRIPLE_LINE  = 1;
	static final double HIDDEN_TRIPLE_BLOCK  = 1;
	static final double XWING = 1;
	static final double SWORDFISH = 1;
	/*
	static final double LOCALIZATION_LINE  = 1.2;
	static final double LOCALIZATION_BLOCK = 1.5;
	static final double NAKED_PAIR_LINE  = 2.5;
	static final double NAKED_PAIR_BLOCK  = 2.5;
	static final double HIDDEN_PAIR_LINE  = 2.5;
	static final double HIDDEN_PAIR_BLOCK  = 2.8;
	static final double NAKED_TRIPLE_LINE  = 3.5;
	static final double NAKED_TRIPLE_BLOCK  = 3.5;
	static final double HIDDEN_TRIPLE_LINE  = 3.8;
	static final double HIDDEN_TRIPLE_BLOCK  = 3.0;
	static final double XWING = 3;
	static final double SWORDFISH = 4.0;
	*/

	private static HashSet<Integer> id = new HashSet<Integer>();
	public static boolean Swordfish(Status state , CandidateTable candPoint){
		boolean updated = false;
		int sz = state.getSize();
		ArrayList<Integer> pair = new ArrayList<Integer>();
		for(int n=1;n<=sz;n++){
			pair.clear();
			for(int i=0;i<sz+sz;i++){
				if(state.getCandCountOfBlock(i, n)>3) {
					pair.add(-1);
					continue;					
				}
				int bit = state.getCandidatePositionMask(i, n);
				pair.add(bit);
			}
			// vertical
			
			for(int i=0;i<sz;i++) if(pair.get(i)>0) 
				for(int j=i+1;j<sz;j++) if(pair.get(j)>0)
					for(int k=j+1;k<sz;k++) if(pair.get(k)>0)
					{
						int intersect = pair.get(i) | pair.get(j) | pair.get(k) ;
						if(Integer.bitCount(intersect) != 3) continue;
						int g1=-1,g2=-1,g3=-1;
						//
						for(int g=0;g<state.getSize();g++){
							if((intersect & (1<<g)) != 0){
								g3 = g2; g2 = g1; g1 = g + sz ;
							}
						}
						int v = 0;
						id.clear();
						id.add(candPoint.setNewID("SWORDFISH"));
						{
							for(int cell_idx : state.getBlock(i)){
								if(cell_idx/sz==(g1-sz) || cell_idx/sz==(g2-sz) || cell_idx/sz==(g3-sz))
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
							for(int cell_idx : state.getBlock(j)){
								if(cell_idx/sz==(g1-sz) || cell_idx/sz==(g2-sz) || cell_idx/sz==(g3-sz))
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
							for(int cell_idx : state.getBlock(k)){
								if(cell_idx/sz==(g1-sz) || cell_idx/sz==(g2-sz) || cell_idx/sz==(g3-sz))
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
						v *= SWORDFISH;
						}
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx%sz == i || cell_idx%sz == j || cell_idx%sz == k) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx%sz == i || cell_idx%sz == j || cell_idx%sz == k) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
						for(int cell_idx : state.getBlock(g3)){
							if(cell_idx%sz == i || cell_idx%sz == j || cell_idx%sz == k) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
					}
			// horizon
			
			for(int i=sz;i<sz+sz;i++) if(pair.get(i)>0) 
				for(int j=i+1;j<sz+sz;j++) if(pair.get(j)>0)
					for(int k=j+1;k<sz+sz;k++) if(pair.get(k)>0)
					{
						int intersect = pair.get(i) | pair.get(j) | pair.get(k) ;
						if(Integer.bitCount(intersect) != 3) continue;
						int g1=-1,g2=-1,g3=-1;
						//
						for(int g=0;g<state.getSize();g++){
							if((intersect & (1<<g)) != 0){
								g3 = g2; g2 = g1; g1 = g ;
							}
						}
						int v = 0;
						id.clear();
						id.add(candPoint.setNewID("SWORDFISH"));
						{
							for(int cell_idx : state.getBlock(i)){
								if(cell_idx%sz==g1 || cell_idx%sz==g2 || cell_idx%sz==g3)
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
							for(int cell_idx : state.getBlock(j)){
								if(cell_idx%sz==g1 || cell_idx%sz==g2 || cell_idx%sz==g3)
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
							for(int cell_idx : state.getBlock(k)){
								if(cell_idx%sz==g1 || cell_idx%sz==g2 || cell_idx%sz==g3)
									continue;
								v += candPoint.get(cell_idx,n).getPoint();
								id.addAll(candPoint.get(cell_idx,n).getTechnique());
							}
							v *= SWORDFISH;
						}
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx/sz == i-sz || cell_idx/sz == j-sz || cell_idx/sz == k-sz) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx/sz == i-sz || cell_idx/sz == j-sz || cell_idx/sz == k-sz) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
						for(int cell_idx : state.getBlock(g3)){
							if(cell_idx/sz == i-sz || cell_idx/sz == j-sz || cell_idx/sz == k-sz) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
					}
		}
		
		return updated;
	}
	
	public static boolean XWing(Status state , CandidateTable candPoint){
		boolean updated = false;
		int sz = state.getSize();
		ArrayList<Integer> pair = new ArrayList<Integer>();
		for(int n=1;n<=sz;n++){
			pair.clear();
			for(int i=0;i<sz+sz;i++){
				if(state.getCandCountOfBlock(i, n)!=2) {
					pair.add(-1);
					continue;					
				}
				int positions = state.getCandidatePositionMask(i, n);
				int firstPosition = Integer.numberOfTrailingZeros(positions);
				positions &= positions - 1;
				int secondPosition = Integer.numberOfTrailingZeros(positions);
				int[] cells = state.getBlock(i);
				int p1 = cells[firstPosition];
				int p2 = cells[secondPosition];
				pair.add(p1*(sz*sz+1)+p2);
			}
			// vertical
			for(int i=0;i<sz;i++) if(pair.get(i)>0) 
				for(int j=i+1;j<sz;j++) if(pair.get(j)>0)
				{
					int cell1_1 = pair.get(i) / (sz*sz+1);
					int cell1_2 = pair.get(i) % (sz*sz+1);
					int cell2_1 = pair.get(j) / (sz*sz+1);
					int cell2_2 = pair.get(j) % (sz*sz+1);
					if(cell1_1/sz > cell1_2 /sz){
						int tmp = cell1_1 ;
						cell1_1 = cell1_2;
						cell1_2 = tmp;
					}
					if(cell2_1/sz > cell2_2 /sz){
						int tmp = cell2_1 ;
						cell2_1 = cell2_2;
						cell2_2 = tmp;
					}
					int v = 0;
					id.clear();
					id.add(candPoint.setNewID("XWING"));
					for(int cell_idx : state.getBlock(i)){
						if(cell_idx == cell1_1 || cell_idx == cell1_2) continue;
						v += candPoint.get(cell_idx,n).getPoint();
						id.addAll(candPoint.get(cell_idx,n).getTechnique());
					}

					for(int cell_idx : state.getBlock(j)){
						if(cell_idx == cell2_1 || cell_idx == cell2_2) continue;
						v += candPoint.get(cell_idx,n).getPoint();
						id.addAll(candPoint.get(cell_idx,n).getTechnique());
					}
					v *= XWING;
					if(cell1_1/sz == cell2_1/sz && cell1_2/sz == cell2_2/sz){
						int g1 = cell1_1/sz + sz;
						int g2 = cell1_2/sz + sz;
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx == cell1_1 || cell_idx == cell2_1) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}		
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx == cell1_2 || cell_idx == cell2_2) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}	
							}
						}
					}
				}
			// horizon
			for(int i=sz;i<sz+sz;i++) if(pair.get(i)>0)
				for(int j=i+1;j<sz+sz;j++) if(pair.get(j)>0)
				{
					int cell1_1 = pair.get(i) / (sz*sz+1);
					int cell1_2 = pair.get(i) % (sz*sz+1);
					int cell2_1 = pair.get(j) / (sz*sz+1);
					int cell2_2 = pair.get(j) % (sz*sz+1);
					if(cell1_1%sz > cell1_2%sz){
						int tmp = cell1_1 ;
						cell1_1 = cell1_2;
						cell1_2 = tmp;
					}
					if(cell2_1%sz > cell2_2%sz){
						int tmp = cell2_1 ;
						cell2_1 = cell2_2;
						cell2_2 = tmp;
					}
					int v = 0;
					id.clear();
					id.add(candPoint.setNewID("XWING"));
					for(int cell_idx : state.getBlock(i)){
						if(cell_idx == cell1_1 || cell_idx == cell1_2) continue;
						v += candPoint.get(cell_idx,n).getPoint();
						id.addAll(candPoint.get(cell_idx,n).getTechnique());
					}

					for(int cell_idx : state.getBlock(j)){
						if(cell_idx == cell2_1 || cell_idx == cell2_2) continue;
						v += candPoint.get(cell_idx,n).getPoint();
						id.addAll(candPoint.get(cell_idx,n).getTechnique());
					}
					v *= XWING;
					if(cell1_1%sz == cell2_1%sz && cell1_2%sz == cell2_2%sz){
						int g1 = cell1_1%sz;
						int g2 = cell1_2%sz;
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx == cell1_1 || cell_idx == cell2_1) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}	
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx == cell1_2 || cell_idx == cell2_2) continue;
							if(state.deleteCandidate(cell_idx, n) || true){
								if(candPoint.get(cell_idx,n).getPoint() > v)
								{
									candPoint.get(cell_idx,n).setPoint(v) ;
									candPoint.get(cell_idx,n).setTechnique(id);
									updated = true;
								}	
							}
						}
					}
				}
		}
		return updated;
		
	}
	
	public static boolean nakedTriple(Status state , CandidateTable candPoint){
		boolean updated = false;

		for(int i=0;i<state.getBlockNum();i++)		
		{
			ArrayList<Integer> idx = new ArrayList<Integer>();
			ArrayList<Integer> pair = new ArrayList<Integer>();

			for(int cell_idx : state.getBlock(i))
			{
				if(state.getCandCountOfCell(cell_idx)<=3){
					int bit = state.getCandidateMask(cell_idx) << 1;
					idx.add(cell_idx);
					pair.add(bit);
				}
			}
			int m = idx.size();
			for(int j=0;j<m;j++)
				for(int k=0;k<j;k++)
					for(int l=0;l<k;l++)
					{
						int intersect = pair.get(j) | pair.get(k) | pair.get(l);
						if(Integer.bitCount(intersect) == 3)
						{
							int p1 = 0;
							int p2 = 0;
							int p3 = 0;
							//
							for(int n=1;n<=state.getSize();n++){
								if((intersect & (1<<n)) != 0){
									p3 = p2; p2 = p1; p1 = n ;
								}
							}
							//
							int v = 0;
							id.clear();
							id.add(candPoint.setNewID("NAKED TRIPLE"));
							for(int n=1;n<=state.getSize();n++)
							{
								if(n==p1 || n==p2 || n==p3) continue;
								v += candPoint.get(idx.get(j),n).getPoint();
								v += candPoint.get(idx.get(k),n).getPoint();
								v += candPoint.get(idx.get(l),n).getPoint();
								id.addAll(candPoint.get(idx.get(j),n).getTechnique());
								id.addAll(candPoint.get(idx.get(k),n).getTechnique());
								id.addAll(candPoint.get(idx.get(l),n).getTechnique());
							}
							//if(v > 1000000) v = 1000000;
							if(state.isVHBlock(i)){
								v *= NAKED_TRIPLE_LINE;
							}else
								v *= NAKED_TRIPLE_BLOCK;
							for(int cell_idx : state.getBlock(i))
							{
								if(idx.get(j)==cell_idx || idx.get(k)==cell_idx || idx.get(l)==cell_idx) continue;

								if(state.deleteCandidate(cell_idx, p1) || true){
									if(candPoint.get(cell_idx,p1).getPoint() > v){
										candPoint.get(cell_idx,p1).setPoint(v) ;
										candPoint.get(cell_idx,p1).setTechnique(id);
										updated = true;
									}
								}
								if(state.deleteCandidate(cell_idx, p2) || true){
									if(candPoint.get(cell_idx,p2).getPoint() > v){
										candPoint.get(cell_idx,p2).setPoint(v) ;
										candPoint.get(cell_idx,p2).setTechnique(id);
										updated = true;
									}
								}
								if(state.deleteCandidate(cell_idx, p3) || true){
									if(candPoint.get(cell_idx,p3).getPoint() > v){
										candPoint.get(cell_idx,p3).setPoint(v) ;
										candPoint.get(cell_idx,p3).setTechnique(id);
										updated = true;
									}
								}
							}
						}
					}
			}
		
		return updated;
	}
	
	public static boolean hiddenTriple(Status state , CandidateTable candPoint){
		boolean updated = false;
		for(int block_idx=0;block_idx<state.getBlockNum();block_idx++)
		{
			ArrayList<Integer> idx = new ArrayList<Integer>();
			ArrayList<Integer> pair = new ArrayList<Integer>();

			for(int n=1;n<=state.getSize();n++){
				if(state.getCandCountOfBlock(block_idx, n)<=3) {
					int bit = state.getCandidatePositionMask(block_idx, n);
					pair.add(bit);
					idx.add(n);
				}
			}
			
			for(int i=0;i<idx.size();i++){
				for(int j=0;j<i;j++)
					for(int k=0;k<j;k++)
					{					
						int intersect = pair.get(i) | pair.get(j) | pair.get(k);
						if(Integer.bitCount(intersect) == 3){
							int cell1 = 0;
							int cell2 = 0;
							int cell3 = 0;
							//
							for(int c=0;c<state.getSize();c++){
								if((intersect & (1<<c)) != 0){
									cell3 = cell2; cell2 = cell1; cell1 = state.getBlock(block_idx)[c] ;
								}
							}
							//
							int v = 0;
							id.clear();
							id.add(candPoint.setNewID("HIDDEN TRIPLE"));
							for(int cell_idx : state.getBlock(block_idx)){
								if(cell_idx == cell1 || cell_idx == cell2 || cell_idx == cell3) continue;
								v += candPoint.get(cell_idx,idx.get(i)).getPoint();
								v += candPoint.get(cell_idx,idx.get(j)).getPoint();
								v += candPoint.get(cell_idx,idx.get(k)).getPoint();
								id.addAll(candPoint.get(cell_idx,idx.get(i)).getTechnique());
								id.addAll(candPoint.get(cell_idx,idx.get(j)).getTechnique());
								id.addAll(candPoint.get(cell_idx,idx.get(k)).getTechnique());
							}

							if(state.isVHBlock(i)){
								v *= HIDDEN_TRIPLE_LINE;
							}else
								v *= HIDDEN_TRIPLE_BLOCK;
							
							//if(state.getCandCountOfCell(cell1)>2){
								for(int n=1;n<=state.getSize();n++){
									if(n==idx.get(i) || n==idx.get(j) || n==idx.get(k)) continue;
									if(state.deleteCandidate(cell1, n) || true){
										if(candPoint.get(cell1,n).getPoint()>v){
											candPoint.get(cell1,n).setPoint(v);
											candPoint.get(cell1,n).setTechnique(id);
											updated = true;
										}
									}
								}
							//}
							//if(state.getCandCountOfCell(cell2)>2){
								for(int n=1;n<=state.getSize();n++){
									if(n==idx.get(i) || n==idx.get(j) || n==idx.get(k)) continue;
									if(state.deleteCandidate(cell2, n) || true){
										if(candPoint.get(cell2,n).getPoint()>v){
											candPoint.get(cell2,n).setPoint(v);
											candPoint.get(cell2,n).setTechnique(id);
											updated = true;
										}
									}
								}
							//}
							//if(state.getCandCountOfCell(cell3)>2){
								for(int n=1;n<=state.getSize();n++){
									if(n==idx.get(i) || n==idx.get(j) || n==idx.get(k)) continue;
									if(state.deleteCandidate(cell3, n) || true){
										if(candPoint.get(cell3,n).getPoint()>v){
											candPoint.get(cell3,n).setPoint(v);
											candPoint.get(cell3,n).setTechnique(id);
											updated = true;
										}
									}
								}
							//}
						}
					}
			}
		}
		return updated;
	}
	public static boolean localization(Status state , CandidateTable candPoint){
		int[] 
			c1  = new int[state.getSize()+1],
			c2  = new int[state.getSize()+1],
			c12 = new int[state.getSize()+1];
		double diffPoint = 0 ; 
		boolean updated = false;
		for(Pair<Integer,Integer> p : state.getBlockConstraint().getIntersetionList())
			{
				int i = p.getFirst();
				int j = p.getSecond();
				Integer[] a = state.getBlockInterSection(i, j);
				if(a.length <= 1) continue;
				
				for(int x=1;x<=state.getSize();x++){
					c1[x] = state.getCandCountOfBlock(i, x);
					c2[x] = state.getCandCountOfBlock(j, x);
					c12[x] = 0 ;
				}
				for(int cell_idx : a){
					for(int x=1;x<=state.getSize();x++){
						if(state.isCand(cell_idx, x)){
							c1[x]--; c2[x]--; c12[x]++;
						}
					}
				}
				//System.out.println("BLOCK " + i + " " + j + " " + c1[9] + " " + c2[9] + " " + c12[9]);
				for(int n=1;n<=state.getSize();n++){
					if(c12[n]==0) continue;
					if(c1[n]>0 && c2[n]==0){
						for(int cell : state.getBlock(i)){
							if(Arrays.binarySearch(a, cell) >= 0) continue;
							if(state.deleteCandidate(cell, n) || true){
								int v = 0 ;
								id.clear();
								id.add(candPoint.setNewID("LOCALIZATION"));
								for(int cell_idx : state.getBlock(j)){
									if(Arrays.binarySearch(a, cell_idx) >= 0) continue;
									v += candPoint.get(cell_idx,n).getPoint();
									id.addAll(candPoint.get(cell_idx, n).getTechnique());
								}
								
								if(state.isVHBlock(j)){
									v *= LOCALIZATION_BLOCK ;
								}else
									v *= LOCALIZATION_LINE;
								if(candPoint.get(cell,n).getPoint() > v)
								{
									candPoint.get(cell,n).setPoint(v) ;
									candPoint.get(cell,n).setTechnique(id);
									updated = true;
								}
								diffPoint += v ;		
							}
						}
					}
					if(c1[n]==0 && c2[n]>0){
						for(int cell : state.getBlock(j)){
							if(Arrays.binarySearch(a, cell) >= 0) continue;
							if(state.deleteCandidate(cell, n) || true){
								int v = 0 ;
								id.clear();
								id.add(candPoint.setNewID("LOCALIZATION"));
								for(int cell_idx : state.getBlock(i)){
									if(Arrays.binarySearch(a, cell_idx) >= 0) continue;
									v += candPoint.get(cell_idx,n).getPoint();
									id.addAll(candPoint.get(cell_idx, n).getTechnique());
								}
								if(state.isVHBlock(i)){
									v *= LOCALIZATION_BLOCK ;
								}else
									v *= LOCALIZATION_LINE;
								if(candPoint.get(cell,n).getPoint() > v)
								{
									candPoint.get(cell,n).setPoint(v) ;
									candPoint.get(cell,n).setTechnique(id);
									updated = true;
								}		
								diffPoint += v ;
							}
						}						
					}
				}
			}
		return updated;
	}

	public static boolean nakedPair(Status state , CandidateTable candPoint){
		boolean updated = false;
		for(int i=0;i<state.getBlockNum();i++)		
		{
			ArrayList<Integer> idx = new ArrayList<Integer>();
			ArrayList<Integer> pair = new ArrayList<Integer>();

			for(int cell_idx : state.getBlock(i))
			{
				if(state.getCandCountOfCell(cell_idx)==2){
					int candidates = state.getCandidateMask(cell_idx);
					int p1 = Integer.numberOfTrailingZeros(candidates) + 1;
					candidates &= candidates - 1;
					int p2 = Integer.numberOfTrailingZeros(candidates) + 1;
					idx.add(cell_idx);
					pair.add(p1*(state.getSize()+1)+p2);
				}
			}
			int m = idx.size();
			for(int j=0;j<m;j++)
				for(int k=0;k<j;k++)
				{
					if(pair.get(j)==pair.get(k))
					{
						int p1 = pair.get(j)/(state.getSize()+1);
						int p2 = pair.get(j)%(state.getSize()+1);
						
						int v = 0;
						id.clear();
						id.add(candPoint.setNewID("NAKED PAIR"));
						for(int n=1;n<=state.getSize();n++)
						{
							if(n==p1 || n==p2) continue;
							v += candPoint.get(idx.get(j),n).getPoint();
							v += candPoint.get(idx.get(k),n).getPoint();
							id.addAll(candPoint.get(idx.get(k),n).getTechnique());
						}
						if(state.isVHBlock(i)){
							v *= NAKED_PAIR_LINE ;
						}else
							v *= NAKED_PAIR_BLOCK ;
						for(int cell_idx : state.getBlock(i))
						{
							if(idx.get(j)==cell_idx || idx.get(k)==cell_idx) continue;
							if(state.deleteCandidate(cell_idx, p1) || true){
								if(candPoint.get(cell_idx,p1).getPoint() > v){
									candPoint.get(cell_idx,p1).setPoint(v);
									candPoint.get(cell_idx,p1).setTechnique(id);
									updated = true;
								}
							}
							if(state.deleteCandidate(cell_idx, p2) || true){
								if(candPoint.get(cell_idx,p2).getPoint() > v){
									candPoint.get(cell_idx,p2).setPoint(v);
									candPoint.get(cell_idx,p2).setTechnique(id);
									updated = true;
								}
							}
						}
					}
				}
		}
		return updated;
	}
	
	public static boolean hiddenPair(Status state , CandidateTable candPoint){
		boolean updated = false;
		for(int block_idx=0;block_idx<state.getBlockNum();block_idx++)
		{
			ArrayList<Integer> idx = new ArrayList<Integer>();
			ArrayList<Integer> pair1 = new ArrayList<Integer>();
			ArrayList<Integer> pair2 = new ArrayList<Integer>();

			for(int n=1;n<=state.getSize();n++){
				if(state.getCandCountOfBlock(block_idx, n)!=2) continue;
				int positions = state.getCandidatePositionMask(block_idx, n);
				int firstPosition = Integer.numberOfTrailingZeros(positions);
				positions &= positions - 1;
				int secondPosition = Integer.numberOfTrailingZeros(positions);
				int[] cells = state.getBlock(block_idx);
				int p1 = cells[firstPosition];
				int p2 = cells[secondPosition];
				idx.add(n);
				pair1.add(p1);
				pair2.add(p2);
			}
			
			for(int i=0;i<idx.size();i++){
				for(int j=0;j<i;j++){
					if(pair1.get(i)==pair1.get(j) && pair2.get(i)==pair2.get(j)){
						int cell1 = pair1.get(i);
						int cell2 = pair2.get(i);
						int v = 0;
						id.clear();
						id.add(candPoint.setNewID("HIDDEN PAIR"));
						for(int cell_idx : state.getBlock(block_idx)){
							if(cell_idx == cell1 || cell_idx == cell2) continue;
							v += candPoint.get(cell_idx,idx.get(i)).getPoint();
							v += candPoint.get(cell_idx,idx.get(j)).getPoint();
							id.addAll(candPoint.get(cell_idx,idx.get(i)).getTechnique());
							id.addAll(candPoint.get(cell_idx,idx.get(j)).getTechnique());
						}
						if(state.isVHBlock(block_idx)){
							v *= HIDDEN_PAIR_LINE;
						}else
							v *= HIDDEN_PAIR_BLOCK;
						//if(state.getCandCountOfCell(cell1)>2){
							for(int n=1;n<=state.getSize();n++){
								if(n==idx.get(i) || n==idx.get(j)) continue;
								if(state.deleteCandidate(cell1, n) || true){
									if(candPoint.get(cell1,n).getPoint()>v){
										candPoint.get(cell1,n).setPoint(v);
										candPoint.get(cell1,n).setTechnique(id);
										updated = true;
									}
								}
							}
						//}
						//if(state.getCandCountOfCell(cell2)>2){
							for(int n=1;n<=state.getSize();n++){
								if(n==idx.get(i) || n==idx.get(j)) continue;
								if(state.deleteCandidate(cell2, n) || true){
									if(candPoint.get(cell2,n).getPoint()>v){
										candPoint.get(cell2,n).setPoint(v);
										candPoint.get(cell2,n).setTechnique(id);
										updated = true;
									}
								}
							}
						//}
					}
				}
			}
		}
		return updated;
	}
	static public Status deleteCandPeer(Status state , int cell_idx , int n
		, CandidateTable candPoint	
		){
		for(int x=1;x<=state.getSize();x++){
			if(x != n){
				removeCand(state,cell_idx, x);
				if(candPoint.get(cell_idx,x).getPoint() > BURIED_CELL_POINT){
					candPoint.get(cell_idx,x).setPoint(BURIED_CELL_POINT);
					candPoint.get(cell_idx,x).setTechnique(null);
				}
			}
		}
		
		for(int block_idx : state.getBlockConstraint().getBlockWhereCellBelong(cell_idx)){
			for(int x : state.getBlock(block_idx)) if(cell_idx != x)
			{
				removeCand(state,x , n);
				if(state.isVHBlock(block_idx)){
					if(candPoint.get(x,n).getPoint() > DELETED_SAME_LINE){
						candPoint.get(x,n).setPoint(DELETED_SAME_LINE);
						candPoint.get(x,n).setTechnique(null);
					}
				}
				else{
					if(candPoint.get(x,n).getPoint() > DELETED_SAME_BLOCK){
						candPoint.get(x,n).setPoint(DELETED_SAME_BLOCK);
						candPoint.get(x,n).setTechnique(null);
					}
				}
					
			}
		}
		return state;
	}
	
	static public Status deleteCandPeer(Status state , int cell_idx , int n){
		for(int x=1;x<=state.getSize();x++){
			if(x != n)
				removeCand(state,cell_idx, x);
		}
		
		for(int block_idx : state.getBlockConstraint().getBlockWhereCellBelong(cell_idx)){
			for(int x : state.getBlock(block_idx)) if(cell_idx != x)
			{
				removeCand(state,x , n);
			}
		}
		return state;
	}
	
	static public Status removeCand(Status state , int cell_idx , int n){
		if(!state.deleteCandidate(cell_idx, n)) return state ;
		return state;
	}
	
	static public Status addNumber(Status state, int cell_idx , int n
			, CandidateTable candPoint
	)
	{
		if(false&&debug) {
			state.showCell();
			for(int y=0;y<state.getSize();y++){
				for(int x=0;x<state.getSize();x++){
					System.out.print(" " + candPoint.get(y*state.getSize()+x,4).getPoint());
				}
				System.out.println();
			}
			
			System.out.println("ADD" + " " + cell_idx/state.getSize() + " " + cell_idx%state.getSize() + " " + n);
		}
		if( state.assignValue(cell_idx ,n)) 
			deleteCandPeer(state, cell_idx ,n , candPoint);
		return state;
	}
	static public Status addNumber(Status state, int cell_idx , int n)
	{
		if( state.assignValue(cell_idx ,n)) 
			deleteCandPeer(state, cell_idx ,n );
		return state;
	}
	public static int countUniqueBlock(Status state){
		int val = 0;
		int n = state.getSize() ;
		for(int block_idx = 0 ; block_idx < state.getBlockNum() ; block_idx++)
		{
			if(state.isVHBlock(block_idx)) continue;
			for(int i=1;i<=n;i++){
				int cnt = 0;
				cnt = state.getCandCountOfBlock(block_idx, i);
				if(cnt == 1){
					boolean ok = false;
					for(int cell_idx : state.getBlock(block_idx)){
						if(state.isCand(cell_idx , i)) {
							if(state.isEmptyCell(cell_idx))	ok = true;
							break;
						}
					}
					if(ok) {
						val ++ ; 
					}
				}
			}
		}
		return val;
	}
	static int minCand = 1<<28;
	static int minCell ;
	static int minN    ;
	static HashSet<Integer> minIDs = new HashSet<Integer>();
	public static double countUniqueBlockWithWeight(Status state , CandidateTable candPoint){
		double val = 0;
		int n = state.getSize() ;
		for(int block_idx = 0 ; block_idx < state.getBlockNum() ; block_idx++)
		{
			int occur = 1 ;
			for(int cell_idx : state.getBlock(block_idx)){
				if(!state.isEmptyCell(cell_idx)) occur ++ ;
			}
			
			for(int i=1;i<=n;i++){
				int cnt = 0;
				cnt = state.getCandCountOfBlock(block_idx, i);
				if(cnt == 1){
					boolean ok = false;
					int f = 0 ;
					for(int cell_idx : state.getBlock(block_idx)){
						if(state.isCand(cell_idx , i)) {
							if(state.isEmptyCell(cell_idx)){
								ok = true;
							}
						}
						if(!state.isEmptyCell(cell_idx)){
							f++;
						}
					}
					if(ok) {
						//val ++ ; 
						
						double v = 0.0;
						int cell = -1;
						id.clear();
						for(int cell_idx : state.getBlock(block_idx)){
							if(!state.isCand(cell_idx, i)){
								v += candPoint.get(cell_idx,i).getPoint();
								id.addAll(candPoint.get(cell_idx,i).getTechnique());
							}else
								cell = cell_idx;
						}

						if(i == preNumber){
							v /= historyCoef;
						}
						if(state.isVHBlock(block_idx)){
							//val += 1.0/(1.0*f + 1.0*(n-1-f) * n);
							v *= UNIQUE_BLOCK;
							val += 1.0 / v ;
							if(minCand > v){
								minCand = (int)v;
								minCell = cell;
								minN    = i;
								minIDs.clear();
								minIDs.addAll(id);
							}
						}
						else{
							v *= UNIQUE_LINE;
							//val += 1.0/(1.0*f + 1.0*(n-1-f) * Math.sqrt(n) );
							val += 1.0 / v;
							//System.out.println("B " + cell + " " + i + " " + v);
							if(minCand * Math.sqrt(n) > v){
								minCand = (int)(v / Math.sqrt(n));
								minCell = cell;
								minN    = i;
								minIDs.clear();
								minIDs.addAll(id);
							}
						}
					}
				}
			}
		}
		return val;
	}
	public static double countUniqueCellWithWeight(Status state , CandidateTable candPoint)
	{
		double val = 0;
		int n = state.getSize() ;
		for(int i=0;i<n*n;i++){
			if(!state.isEmptyCell(i)) continue;
			if(state.getCandCountOfCell(i)==1){
				double v = 0.0;
				int tarN = -1;
				id.clear();
				for(int j=1;j<=n;j++){
					if(!state.isCand(i, j)){
						v += candPoint.get(i,j).getPoint();
						id.addAll(candPoint.get(i,j).getTechnique());
					}else
						tarN = j ;
				}
				//val += 1/((3*n + 2 * Math.sqrt(n) - 1) * n);
				if(tarN == preNumber){
					v /= historyCoef ;
				}
				v *= UNIQUE_CELL ; 
				val += 1 / v ; 
				if(minCand > v){
					minCand = (int)v;
					minCell = i;
					minIDs.clear();
					minIDs.addAll(id);
					minN    = tarN;
				}
			}
		}
		return val;
	}
	
	public static double eval2(Status state  ,CandidateTable candPoint)
	{
		double val=0;
		int step = 0;
		int n = state.getSize();
		preNumber = -1;
		TreeSet<Integer> s = new TreeSet<Integer>();
		while(state.getSpaceCount() > 0){
			boolean added = false;
			step ++ ; 
			double find = 0.0;
			{ // evaluating a grid status
				minCand = 1<<28;
				minCell = -1;
				minIDs.clear();
				find = countUniqueBlockWithWeight(state,candPoint)+ countUniqueCellWithWeight(state,candPoint);
				if(find > 1e-8)
					val += state.getSpaceCount() / find ;
				
				//System.out.println(minCell + " " + minN );
				if(debug) System.out.println("space : " + state.getSpaceCount() + " " + countUniqueBlock(state) + " " 
						+ state.getSpaceCount()/find + " " + countUniqueBlockWithWeight(state,candPoint) + " " + countUniqueCellWithWeight(state,candPoint));
				//state.showCell();
				//state.showCandData();
				if(minCell >= 0 && minN >= 1){
					//System.out.println(" " + minCand);
					//val += minCand;
					preNumber = minN ;
					addNumber(state, minCell, minN ,candPoint);
					for(int ids : minIDs){
						//System.out.println(ids + " " + candPoint.id2procedure.get(ids));
						s.add(ids);
					}
				}
			}
			
			if(find < 1e-9){
				if(Evaluator.localization(state,candPoint)){
					if(debug) System.out.println("LOCALIZATION");
					continue;
				}
				if(Evaluator.nakedPair   (state, candPoint)){
					Evaluator.hiddenPair  (state, candPoint);
					Evaluator.XWing(state, candPoint);
					if(debug) System.out.println("NAKED PAIR");
					continue;
				}
				if(Evaluator.hiddenPair  (state, candPoint)){
					Evaluator.XWing(state, candPoint);
					if(debug) System.out.println("HIDDEN PAIR");
					continue;
				}
				if(Evaluator.XWing(state, candPoint)){
					if(debug) System.out.println("X WING");
					continue;					
				}
				if(Evaluator.nakedTriple(state, candPoint)){
					Evaluator.hiddenTriple(state, candPoint);
					Evaluator.Swordfish(state, candPoint);
					if(debug) System.out.println("NAKED TRIPLE");
					continue;					
				}
				if(Evaluator.hiddenTriple(state, candPoint)){
					Evaluator.Swordfish(state, candPoint);
					if(debug) System.out.println("HIDDEN TRIPLE");
					continue;					
				}
				if(Evaluator.Swordfish(state, candPoint)){
					if(debug) System.out.println("SWORDFISH");
					continue;					
				}
				System.out.println("NOT SOLVED");
				break;
			}
			if(true) continue;
			ArrayList<Pair<Integer,Integer> > cand = new ArrayList<Pair<Integer,Integer>>();
			for(int block_idx = 0 ; block_idx < state.getBlockNum() ; block_idx++)
			{
				int space = 0 ;
				int space_idx = -1 ;
				for(int cell_idx : state.getBlock(block_idx)){
					if(state.isEmptyCell(cell_idx)) {
						space ++ ;
						space_idx = cell_idx ; 
					}
				}
				if(space != 1) continue;
				int x = state.UniqueCandidateNumberOfCell(space_idx);
				if(x == -1){
					continue;
				}
				//addNumber(state, space_idx, x);
				cand.add(new Pair<Integer,Integer>(space_idx,x));
				added = true;
			}
			if(added) {
				// int p = Utility.random(cand.size());
				// addNumber(state, cand.get(p).getFirst(), cand.get(p).getSecond(),candPoint);
				continue;
			}
			for(int block_idx = 0 ; block_idx < state.getBlockNum() ; block_idx++)
			{
				if(state.isVHBlock(block_idx)) continue;
				for(int i=1;i<=n;i++){
					int cnt = 0;
					cnt = state.getCandCountOfBlock(block_idx, i);
					if(cnt == 1){
						int obj_idx = -1;
						for(int cell_idx : state.getBlock(block_idx)){
							if(!state.isEmptyCell(cell_idx)) continue;
							if(state.isCand(cell_idx, i)){
								obj_idx = cell_idx ; break;
							}
						}
						if(obj_idx==-1) continue;
						int x = state.UniqueCandidateNumberOfCell(obj_idx);
						if(i != x){
							System.out.println("ERROR");
						}
						//addNumber(state, obj_idx, i);
						cand.add(new Pair<Integer,Integer>(obj_idx,i));
						added = true;
						break;
					}
				}
				if(added) break;
			}
			if(added) {
				// int p = Utility.random(cand.size());
				// addNumber(state, cand.get(p).getFirst(), cand.get(p).getSecond(),candPoint);
				continue;
			}
			for(int block_idx = 0 ; block_idx < state.getBlockNum() ; block_idx++)
			{
				if(!state.isVHBlock(block_idx)) break;
				
				for(int i=1;i<=n;i++){
					int cnt = 0;
					cnt = state.getCandCountOfBlock(block_idx, i);
					if(cnt == 1){
						int obj_idx = -1;
						for(int cell_idx : state.getBlock(block_idx)){
							if(!state.isEmptyCell(cell_idx)) continue;
							if(state.isCand(cell_idx, i)){
								obj_idx = cell_idx ; break;
							}
						}
						if(obj_idx==-1) continue;
						int x = state.UniqueCandidateNumberOfCell(obj_idx);
						if(i != x){
							System.out.println("ERROR");
						}
						//addNumber(state, obj_idx, i);
						cand.add(new Pair<Integer,Integer>(obj_idx,i));
						added = true;
						break;
					}
				}
				if(added) break;
			}
			if(added) {
				// int p = Utility.random(cand.size());
				// addNumber(state, cand.get(p).getFirst(), cand.get(p).getSecond(),candPoint);
				continue;
			}
			for(int cell_idx = 0 ; cell_idx < state.getCellSize() ; cell_idx++)
			{
				if(!state.isEmptyCell(cell_idx)) continue;
				int x = state.UniqueCandidateNumberOfCell(cell_idx);
				if(x != -1){
					addNumber(state, cell_idx, x,candPoint);
					added = true;
					break;
				}
			}
			if(!added) break;
		}
		/*System.out.println("TEST");
		for(int ids : s){
			System.out.println(ids + " " + candPoint.id2procedure.get(ids));
		}*/
		return val;
	}
	
	public static double evaluate(int numSize , BlockConstraint block , int[] cell){
		Status state = new Status(numSize , block);
		CandidateTable candPoint = new CandidateTable(state.getCellSize(),state.getSize()+1);		
		for(int i=0;i<state.getCellSize();i++){
			if(cell[i] > 0){
				Evaluator.addNumber(state, i, cell[i] , candPoint);
			}
		}
		return Evaluator.eval2(state , candPoint);
	}

	
}
