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

public class Generator {
	public final int RESERVED = -1 , SPACE = 0;
	public final SolverMethod seedMethod ;
	
	public SolverMethod method ; 
	// マスに入る数字の個数
	private int numSize ;
	
	// 明示的なヒント
	private int[] hint;
	
	// 解のSEED
	private int[] seed;
	
	// 解の条件（暗示的）
	private int[] hidden;

	public int forbidden = -1;
	
	int[] hiddenList;
	
	int[] hintList;
	private final int[] candidateScratch;
	
	BlockConstraint block;
	private final JavaRandom random;
	
	int[][] group;

	Status original ;
	Status statussub ;
	Status group_status ;	
	
	private boolean interruption;
	public void setInterruption( boolean b ) {
		this.interruption = b;
	}
	
	public void setForbidden(int x){
		this.forbidden = x ;
	}
	
	public void showDebugData(){
		System.out.println("num : " + numSize);
		System.out.print("hint : ");
		for(int x : hintList) System.out.print(" " + x); System.out.println();
		System.out.print("hidden : ");
		for(int x : hiddenList) System.out.print(" " + x); System.out.println();
	}
		public Generator(int num_size , int[] hint , int[] hidden ,
				BlockConstraint block ) {
			this(num_size, hint, hidden, block, new JavaRandom(0));
		}
		public Generator(int num_size , int[] hint , int[] hidden ,
				BlockConstraint block, JavaRandom random ) {
			this(num_size, hint, hidden, block, random, null);
		}
		public Generator(int num_size , int[] hint , int[] hidden ,
				BlockConstraint block, JavaRandom random, int[] seed ) {
			this.numSize = num_size;
			this.hint = hint;
			this.hidden  = hidden;
			this.block = block;
			this.random = random;
			this.seed = seed == null ? null : seed.clone();
			this.candidateScratch = new int[num_size + 1];
			this.seedMethod = new SolverMethod();
		this.method = new SolverMethod();
		original = new Status(numSize , block);
		statussub = new Status(numSize , block);
		group_status = new Status(numSize , block);
		seedMethod.localization = true;
		seedMethod.nakedPair = true;
		seedMethod.hiddenPair = true;
		initialize();
	}
	public int[] getSeed(){
		return seed;
	}
	public void setMethod(SolverMethod method){
		this.method = method;
	}
	private void initialize(){
		int hiddenCount = 0;
		int hintCount = 0;
		for(int value : hidden) if(value != 0) hiddenCount++;
		for(int value : hint) if(value != 0) hintCount++;
		hiddenList = new int[hiddenCount];
		hintList = new int[hintCount];
		int hiddenCursor = 0;
		int hintCursor = 0;
		for(int i=0;i<hidden.length;i++)
			if(hidden[i] != 0) hiddenList[hiddenCursor++] = i;
		for(int i=0;i<hint.length;i++)
			if(hint[i]!=0) hintList[hintCursor++] = i;
		
		{  
			int GROUP = Utility.sqrt(hintList.length) ;
			if(GROUP == 0) GROUP = 1;
			group = new int [GROUP][];
			int remain = hintList.length % GROUP;
			int unit   = hintList.length / GROUP;
			int cursor = 0;
			for(int i=0;i<GROUP;i++){
				int elements = unit ; 
				if(i < remain) elements++;
				group[i] = new int[elements];
				for(int j=0;j<elements;j++){
					group[i][j] = hintList[cursor++];
				}
			}
		}
	}

	private int[] generateSeedSub(){
		int[] stat = new int[hidden.length];
		System.arraycopy(hidden, 0, stat, 0, hidden.length);
		Status status = new Status(numSize, block); 
		{
			/*Solver solver = new Solver(new Status(stat , numSize, block));
			solver.setMethod(seedMethod);*/
			for(int i=0;i<stat.length;i++) {
				if(stat[i]>0) {
					Solver.addNumber(status,i, stat[i]);
				}
			}
			status = Solver.answer(status,seedMethod);
			if(status.getKindOfAnswer() == Solver.KindOfAnswer.IRREGULAR_PROBLEM){
				System.out.println("NO");
			}
		}
		for(int i=0;i<stat.length;i++){
			if(status.isEmptyCell(i) == false) continue;
			int candnum = status.getCandCountOfCell(i);
			if(candnum==0) {
				//System.out.println("NO ANSWER");
				return null;
			}
			if(status.isNoAnswer()){
				//System.out.println("NO ANSWER 2"); TODO
				return null;
			}
			int r = random.nextInt(candnum) ;
			int x = status.getNthCandOfCell(i, r );
			if(hint[i]!=0 && x == forbidden){
				if(candnum==1) return null;
				x = status.getNthCandOfCell(i,(r+1)%candnum);
			}
			//status.assignValue(i, x); 
			//Solver solver = new Solver(status);
			//solver.setMethod(seedMethod);
			Solver.addNumber(status, i, x);
			status = Solver.answer(status , seedMethod);
		}
		if(forbidden > 0)
			for(int cell_idx : hintList)
				if(status.getCell(cell_idx)==forbidden) return null;
		if(status.getSpaceCount()!=0) return null;
		return status.getCell();
	}
	private int [] generateSeed(){
		int[] val ; 
		int failed_num = 0; 
		while(true){
			if( interruption )
				return null;
			val = generateSeedSub() ; 
			if(val != null) {
				//System.out.println("seed : " + failed_num);
				return val;
			}
			failed_num ++ ;
			if(failed_num > 100) {
				//	System.out.println("Not seed gained.");
				return null;
			}
		}
	}
	public boolean fitToHidden(int [] cell){
		for(int i : hiddenList) {
			if(cell[i] != hidden[i]) return false;
		}
		return true;
	}
	public int[] generate(){
		//if(true) return generateold();
		interruption = false;
		seed = generateSeed(); 
		if(seed == null) return null;
		int[] stat = new int[hidden.length];
		for(int cell_idx : hintList){
			stat[cell_idx] = seed[cell_idx];
		}
		//Solver solver = new Solver(new Status(stat , numSize , block));
		Status status = new Status(numSize , block);
		status.setUniqueMethod(this.method.unique);
		for(int i=0;i<stat.length;i++)
			if(stat[i]>0){
				Solver.addNumber(status , i, stat[i]);
			}
		status = Solver.answer(status, this.method);
		if(status.getKindOfAnswer() == Solver.KindOfAnswer.UNIQUE_ANSWER){
			return stat;
		}	
		original    .clear();
		statussub   .clear();
		group_status.clear();
		original    .setUniqueMethod(this.method.unique);
		statussub   .setUniqueMethod(this.method.unique);
		group_status.setUniqueMethod(this.method.unique);
		random.shuffle(hintList);
		int zero = status.getSpaceCount();
		if(zero == 0) return stat;
		boolean yet = true; 
		while(yet){
			yet = false;
			for(int i = 0 ; i < group.length ; i++){
				int[] g= group[i];
				group_status.clear();
				for(int j=0;j<group.length;j++) if(i!=j){
					for(int x : group[j]){
						Solver.addNumber(group_status, x , stat[x]);
					}
				}
				for(int cell_idx : g){
					if(hidden[cell_idx]!=0) continue;
					if( interruption )
						return null;					
					int pre = stat[cell_idx] ;
					
					stat[cell_idx] = 0 ;
					original.copyStatusToThis(group_status);
					//original = group_status.dup();
					for(int x : g) if(x != cell_idx) {
						Solver.addNumber(original, x, stat[x]);
					}
					if(original.getCandCountOfCell(cell_idx)<=1){
						stat[cell_idx] = pre ; 
						continue;
					}
					int candidateCount = 0;
					int candidateMask = original.getCandidateMask(cell_idx);
					for(int n=1;n<=numSize;n++)
						if((candidateMask & (1 << (n-1))) != 0)
							candidateScratch[candidateCount++] = n;
					int preIndex = -1;
					for(int candidateIndex=0;candidateIndex<candidateCount;candidateIndex++)
						if(candidateScratch[candidateIndex] == pre) preIndex = candidateIndex;
					if(preIndex < 0){
						preIndex = candidateCount;
						candidateScratch[candidateCount++] = pre;
					}
					stat[cell_idx] = pre ; 
					
					for(int offset=1;offset<candidateCount;offset++){
						int next_cond = candidateScratch[(preIndex + offset) % candidateCount];
						if(next_cond==pre) continue;
						if(next_cond==forbidden) continue;
						statussub.copyStatusToThis(original);
						//Solver solve2 = new Solver(statussub);
						//solve2.addNumber(cell_idx, next_cond);
						Solver.addNumber(statussub, cell_idx, next_cond);
						statussub = Solver.answer(statussub , method);
						int space = statussub.getSpaceCount();
						//int candnum  = statussub.getCandCountOfCell(cell_idx);
						if(statussub.isNoAnswer()){		
							//System.out.println("no answer");
						}else if(fitToHidden(statussub.getCell())==false){
							//System.out.println("paradox");		
						}else {
							if(zero > space){			
								zero = space;
								stat[cell_idx] = next_cond;			
								if(zero == 0) {
									return stat;
								}
								yet = true; break;
							}
						}
					}
					if(yet) break;
					
				}
			}
		}
		if(zero != 0) return null;
		return stat;
	}
	
}
