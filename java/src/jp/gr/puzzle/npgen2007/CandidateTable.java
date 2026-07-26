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

import java.util.HashMap;
import java.util.HashSet;

class CandidateData
{
	private HashSet<Integer> technique ;
	private int point ;
	
	public CandidateData() {
		technique = new HashSet<Integer>();
		point     = 1<<29;
	}
	
	public void setPoint(int point){
		this.point = point;
	}
	public int getPoint(){
		return point;
	}
	public void setTechnique(HashSet<Integer> technique){
		this.technique.clear();
		if(technique != null)
			this.technique.addAll(technique);
	}
	public HashSet<Integer> getTechnique(){
		return technique;
	}
}

public class CandidateTable{
	private CandidateData[][] table;
	private int idNumber = 0;
	HashMap<Integer, String> id2procedure = new HashMap<Integer, String>(); 
	public CandidateTable(int cellN , int N){
		table = new CandidateData[cellN][N];
		for(int i=0;i<cellN;i++) for(int j=0;j<N;j++)
			table[i][j] = new CandidateData();
	}	
	public CandidateData get(int cell_idx , int n){
		return table[cell_idx][n];
	}
	public int setNewID(String procedure){
		id2procedure.put(idNumber, procedure);
		return idNumber++;
	}
}
