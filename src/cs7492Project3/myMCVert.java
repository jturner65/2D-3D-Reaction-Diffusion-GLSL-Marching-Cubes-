package cs7492Project3;

//class to store vertexes of triangle - will calculate normal based on all triangles that share this vertex
public class myMCVert{
	public myPointf loc = new myPointf();
	public myVectorf n = new myVectorf();
	public myMCVert(){	}
		
	public void setVertLoc(myPointf _loc){
		loc.set(_loc);		
	}
	
	//set the location of this vert, and the initial normal and # tris
	public void setNorm(myVectorf _norm){
		n._add(_norm);
	}
		
}//

