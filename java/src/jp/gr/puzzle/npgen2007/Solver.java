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

import java.util.*;

public class Solver {
	public static enum KindOfAnswer{
		// 唯一解 or 解なし or 複数解 or 不正
		UNIQUE_ANSWER , NO_ANSWER , MULTIPLE_ANSWER , IRREGULAR_PROBLEM , NO_JUDGE
	} ;
	
	public static boolean Swordfish(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int[] pair = new int[sz + sz];
		for(int n=1;n<=sz;n++){
			for(int i=0;i<sz+sz;i++){
				pair[i] = state.getCandCountOfBlock(i, n)>3
						? -1 : state.getCandidatePositionMask(i, n);
			}
			// vertical
			
			for(int i=0;i<sz;i++) if(pair[i]>0)
				for(int j=i+1;j<sz;j++) if(pair[j]>0)
					for(int k=j+1;k<sz;k++) if(pair[k]>0)
					{
						int intersect = pair[i] | pair[j] | pair[k] ;
						if(Integer.bitCount(intersect) != 3) continue;
						int excluded = (1<<i) | (1<<j) | (1<<k);
						while(intersect != 0){
							int position = 31 - Integer.numberOfLeadingZeros(intersect);
							int block_idx = position + sz;
							int[] cells = state.getBlock(block_idx);
							int targets = state.getCandidatePositionMask(block_idx, n) & ~excluded;
							while(targets != 0){
								int target = Integer.numberOfTrailingZeros(targets);
								if(removeCand(state,cells[target],n)) updated = true;
								targets &= targets - 1;
							}
							intersect &= ~(1 << position);
						}
					}
			// horizon
			
			for(int i=sz;i<sz+sz;i++) if(pair[i]>0)
				for(int j=i+1;j<sz+sz;j++) if(pair[j]>0)
					for(int k=j+1;k<sz+sz;k++) if(pair[k]>0)
					{
						int intersect = pair[i] | pair[j] | pair[k] ;
						if(Integer.bitCount(intersect) != 3) continue;
						int excluded = (1<<(i-sz)) | (1<<(j-sz)) | (1<<(k-sz));
						while(intersect != 0){
							int block_idx = 31 - Integer.numberOfLeadingZeros(intersect);
							int[] cells = state.getBlock(block_idx);
							int targets = state.getCandidatePositionMask(block_idx, n) & ~excluded;
							while(targets != 0){
								int target = Integer.numberOfTrailingZeros(targets);
								if(removeCand(state,cells[target],n)) updated = true;
								targets &= targets - 1;
							}
							intersect &= ~(1 << block_idx);
						}
					}
		}
		
		return updated;
	}
	
	public static boolean XWing(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int divisor = sz*sz+1;
		int[] pair = new int[sz + sz];
		for(int n=1;n<=sz;n++){
			for(int i=0;i<sz+sz;i++){
				if(state.getCandCountOfBlock(i, n)!=2) {
					pair[i] = -1;
					continue;					
				}
				int positions = state.getCandidatePositionMask(i, n);
				int first = Integer.numberOfTrailingZeros(positions);
				positions &= positions - 1;
				int second = Integer.numberOfTrailingZeros(positions);
				int[] cells = state.getBlock(i);
				pair[i] = cells[first] * divisor + cells[second];
			}
			// vertical
			for(int i=0;i<sz;i++) if(pair[i]>0)
				for(int j=i+1;j<sz;j++) if(pair[j]>0)
				{
					int cell1_1 = pair[i] / divisor;
					int cell1_2 = pair[i] % divisor;
					int cell2_1 = pair[j] / divisor;
					int cell2_2 = pair[j] % divisor;
					if(cell1_1/sz == cell2_1/sz && cell1_2/sz == cell2_2/sz){
						int g1 = cell1_1/sz + sz;
						int g2 = cell1_2/sz + sz;
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx == cell1_1 || cell_idx == cell2_1) continue;
							if(removeCand(state,cell_idx, n)){
								updated = true;
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx == cell1_2 || cell_idx == cell2_2) continue;
							if(removeCand(state,cell_idx, n)){
								updated = true;
							}
						}
					}
				}
			// horizon
			for(int i=sz;i<sz+sz;i++) if(pair[i]>0)
				for(int j=i+1;j<sz+sz;j++) if(pair[j]>0)
				{
					int cell1_1 = pair[i] / divisor;
					int cell1_2 = pair[i] % divisor;
					int cell2_1 = pair[j] / divisor;
					int cell2_2 = pair[j] % divisor;
					if(cell1_1%sz == cell2_1%sz && cell1_2%sz == cell2_2%sz){
						int g1 = cell1_1%sz;
						int g2 = cell1_2%sz;
						for(int cell_idx : state.getBlock(g1)){
							if(cell_idx == cell1_1 || cell_idx == cell2_1) continue;
							if(removeCand(state,cell_idx, n)){
								updated = true;
							}
						}
						for(int cell_idx : state.getBlock(g2)){
							if(cell_idx == cell1_2 || cell_idx == cell2_2) continue;
							if(removeCand(state,cell_idx, n)){
								updated=true;
							}
						}
					}
				}
		}
		return updated;
		
	}
	
	public static boolean nakedTriple(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int[] id = new int[sz];
		int[] pair = new int[sz];

		for(int i=0;i<state.getBlockNum();i++)		
		{
			int count = 0;
			int[] cells = state.getBlock(i);
			for(int cell_idx : cells)
			{
				if(state.getCandCountOfCell(cell_idx)<=3){
					id[count] = cell_idx;
					pair[count] = state.getCandidateMask(cell_idx) << 1;
					count++;
				}
			}
			for(int j=0;j<count;j++)
				for(int k=0;k<j;k++)
					for(int l=0;l<k;l++)
					{
						int intersect = pair[j] | pair[k] | pair[l];
						if(Integer.bitCount(intersect) == 3)
						{
							for(int cell_idx : cells)
							{
								if(id[j]==cell_idx || id[k]==cell_idx || id[l]==cell_idx) continue;
								int values = intersect & (state.getCandidateMask(cell_idx) << 1);
								while(values != 0){
									int n = 31 - Integer.numberOfLeadingZeros(values);
									if(removeCand(state,cell_idx,n)) updated = true;
									values &= ~(1 << n);
								}
							}
						}
					}
			}
		
		return updated;
	}
	
	public static boolean hiddenTriple(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int[] id = new int[sz];
		int[] pair = new int[sz];
		for(int block_idx=0;block_idx<state.getBlockNum();block_idx++)
		{
			int count = 0;
			int[] cells = state.getBlock(block_idx);
			for(int n=1;n<=sz;n++){
				if(state.getCandCountOfBlock(block_idx, n)<=3) {
					pair[count] = state.getCandidatePositionMask(block_idx, n);
					id[count] = n;
					count++;
				}
			}
			
			for(int i=0;i<count;i++){
				for(int j=0;j<i;j++)
					for(int k=0;k<j;k++)
					{					
						int intersect = pair[i] | pair[j] | pair[k];
						if(Integer.bitCount(intersect) == 3){
							while(intersect != 0){
								int position = 31 - Integer.numberOfLeadingZeros(intersect);
								int cell = cells[position];
								if(state.getCandCountOfCell(cell)>2){
									for(int n=1;n<=sz;n++){
										if(n==id[i] || n==id[j] || n==id[k]) continue;
										if(removeCand(state,cell,n)) updated = true;
									}
								}
								intersect &= ~(1 << position);
							}
						}
					}
			}
		}
		return updated;
	}
	public static boolean localization(Status state){
		boolean updated = false;
		int sz = state.getSize();
		for(int[] detail : state.getBlockConstraint().getIntersectionDetails()){
			int first = detail[0];
			int second = detail[1];
			for(int n=1;n<=sz;n++){
				int firstPositions = state.getCandidatePositionMask(first,n);
				int secondPositions = state.getCandidatePositionMask(second,n);
				int shared = Integer.bitCount(firstPositions & detail[2]);
				if(shared==0) continue;
				int target = -1;
				int commonMask = 0;
				if(state.getCandCountOfBlock(first,n)>shared
						&& state.getCandCountOfBlock(second,n)==shared){
					target = first;
					commonMask = detail[2];
				}else if(state.getCandCountOfBlock(first,n)==shared
						&& state.getCandCountOfBlock(second,n)>shared){
					target = second;
					commonMask = detail[3];
				}
				if(target>=0){
					int targets = state.getCandidatePositionMask(target,n) & ~commonMask;
					int[] cells = state.getBlock(target);
					while(targets!=0){
						int position = Integer.numberOfTrailingZeros(targets);
						if(removeCand(state,cells[position],n)) updated = true;
						targets &= targets - 1;
					}
				}
			}
		}
		return updated;
	}

	public static boolean nakedPair(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int[] id = new int[sz];
		int[] pair = new int[sz];
		for(int i=0;i<state.getBlockNum();i++)		
		{
			int count = 0;
			int[] cells = state.getBlock(i);
			for(int cell_idx : cells)
			{
				if(state.getCandCountOfCell(cell_idx)==2){
					int candidates = state.getCandidateMask(cell_idx);
					int p1 = Integer.numberOfTrailingZeros(candidates)+1;
					candidates &= candidates - 1;
					int p2 = Integer.numberOfTrailingZeros(candidates)+1;
					id[count] = cell_idx;
					pair[count] = p1*(sz+1)+p2;
					count++;
				}
			}
			for(int j=0;j<count;j++)
				for(int k=0;k<j;k++)
				{
					if(pair[j]==pair[k])
					{
						int p1 = pair[j]/(sz+1);
						int p2 = pair[j]%(sz+1);
						for(int cell_idx : cells)
						{
							if(id[j]==cell_idx || id[k]==cell_idx) continue;
							if(removeCand(state,cell_idx, p1)){
								updated = true;
							}
							if(removeCand(state,cell_idx, p2)){
								updated = true;
							}
						}
					}
				}
		}
		return updated;
	}
	
	public static boolean hiddenPair(Status state){
		boolean updated = false;
		int sz = state.getSize();
		int[] id = new int[sz];
		int[] pair1 = new int[sz];
		int[] pair2 = new int[sz];
		for(int block_idx=0;block_idx<state.getBlockNum();block_idx++)
		{
			int count = 0;
			int[] cells = state.getBlock(block_idx);
			for(int n=1;n<=sz;n++){
				if(state.getCandCountOfBlock(block_idx, n)!=2) continue;
				int positions = state.getCandidatePositionMask(block_idx,n);
				int first = Integer.numberOfTrailingZeros(positions);
				positions &= positions - 1;
				int second = Integer.numberOfTrailingZeros(positions);
				id[count] = n;
				pair1[count] = cells[first];
				pair2[count] = cells[second];
				count++;
			}
			
			for(int i=0;i<count;i++){
				for(int j=0;j<i;j++){
					if(pair1[i]==pair1[j] && pair2[i]==pair2[j]){
						int cell1 = pair1[i];
						int cell2 = pair2[i];
						if(state.getCandCountOfCell(cell1)>2){
							for(int n=1;n<=sz;n++){
								if(n==id[i] || n==id[j]) continue;
								if(removeCand(state,cell1, n)){
									updated = true;
								}
							}
						}
						if(state.getCandCountOfCell(cell2)>2){
							for(int n=1;n<=sz;n++){
								if(n==id[i] || n==id[j]) continue;
								if(removeCand(state,cell2, n)){
									updated = true;
								}
							}
						}
					}
				}
			}
		}
		return updated;
	}
	static public Status answer(Status state , SolverMethod method) {
		state.unique = method.unique;
		{
			int [] cell = state.getCell();
			for(int i=0;i<cell.length;i++)
				if(cell[i] != 0){
					if(state.isCand(i,cell[i]) == false){
						state.setKindOfAnswer(Solver.KindOfAnswer.IRREGULAR_PROBLEM);
					}
					deleteCandPeer(state, i, cell[i]);
				}
		}
		if(state.isInvalid()){
			return state;
		}
		boolean updated = true;
		while(updated) {
			updated = false;
			if(state.getSpaceCount()==0) break;
			if(method.localization && updated == false) {
				updated = localization(state);
				//if(updated) System.out.println("SOLVE LOCALIZATION");				
			}
			if(method.nakedPair && updated == false) {
				updated = nakedPair(state);
				//if(updated) System.out.println("SOLVE NAMED PAIR");
			}
			if(method.hiddenPair && updated == false) {
				updated = hiddenPair(state);
				//if(updated) System.out.println("SOLVE HIDDEN PAIR");
			}
			if(method.XWing && updated == false){
				updated = XWing(state);
				//if(updated) System.out.println("SOLVE XWing");
			}
			if(method.nakedTriple && updated == false){
				updated = nakedTriple(state);
				//if(updated) System.out.println("SOLVE nakedTriple");
			}

			if(method.hiddenTriple && updated == false){
				updated = hiddenTriple(state);
				//if(updated) System.out.println("SOLVE hiddenTriple");
			}

			if(method.swordfish && updated == false){
				updated = Swordfish(state);
				//if(updated) System.out.println("SOLVE swordfish");
			}
			if(state.isNoAnswer()) return state;
		}
		//System.out.println("Space count : " + state.getSpaceCount());
		if(state.getSpaceCount()>0)
			state.setKindOfAnswer(KindOfAnswer.MULTIPLE_ANSWER);
		else 
			state.setKindOfAnswer(KindOfAnswer.UNIQUE_ANSWER);
		return state; 
	}

	static private Status deleteCandPeer(Status state , int cell_idx , int n){
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
	
	static private boolean removeCand(Status state , int cell_idx , int n){
		if(!state.deleteCandidate(cell_idx, n)) return false ;
		if(state.unique.cellUnique && state.isUniqueCandidate(cell_idx) && state.isEmptyCell(cell_idx)){
			addNumber(state , cell_idx, state.getUniqueCandidate(cell_idx));
			//addNumber(state , cell_idx, Bit.ntz(state.getCell(cell_idx)) + 1);
		}
		BlockConstraint block = state.getBlockConstraint();
		for (int block_idx : block.getBlockWhereCellBelong()[cell_idx])
		{
			if(!state.unique.vhUnique    &&  state.isVHBlock(block_idx)) continue;
			if(!state.unique.blockUnique && !state.isVHBlock(block_idx)) continue;
			int positions = state.getCandidatePositionMask(block_idx, n);
			if(positions != 0 && (positions & (positions - 1)) == 0){
				addNumber(state, state.getBlock(block_idx)
						[Integer.numberOfTrailingZeros(positions)], n);
			}
		}
		return true;
	}
	
	static public boolean addNumber(Status state, int cell_idx , int n)
	{
		if( state.assignValue(cell_idx ,n)) {
			deleteCandPeer(state, cell_idx ,n);
			return true;
		}
		return false;
	}
	
	static public boolean addNumbers( Status state, int[] cells ) {
		boolean ok = true;
		for(int i=0;i<cells.length;i++){
			if(cells[i]>0){
				ok &= Solver.addNumber(state , i, cells[i]);
			}
		}
		return ok;
	}
		
}
