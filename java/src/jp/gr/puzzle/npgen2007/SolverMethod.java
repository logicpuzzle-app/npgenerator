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

public class SolverMethod{
	public boolean localization;
	public boolean nakedPair ;
	public boolean hiddenPair;
	public boolean nakedTriple;
	public boolean hiddenTriple;
	public boolean XWing;
	public boolean swordfish;
	public UniqueMethod unique = new UniqueMethod();
	public SolverMethod() {
		localization = false;
		nakedPair    = false;
		hiddenPair   = false;
		nakedTriple  = false;
		hiddenTriple = false;
		XWing        = false;
		swordfish    = false;
	}
	public void setAllUse(){
		localization = true;
		nakedPair    = true;
		hiddenPair   = true;
		nakedTriple  = true;
		hiddenTriple = true;
		XWing        = true;
		swordfish    = true;
		unique.setAllUse();
	}
}
