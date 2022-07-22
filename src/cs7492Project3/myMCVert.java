package cs7492Project3;

//class to store vertexes of triangle - will calculate normal based on all triangles that share this vertex
public class myMCVert{
	public int numTris = 0;
	public myPointf loc = new myPointf();
	public myVectorf n = new myVectorf();			//raw unit normal == actual normal * # tris sharing this vertex
	public myMCVert(){
	//	clearVert();
	}
	public void clearVert(){
		numTris = 0;
		loc.set(0,0,0);
		n.set(0,0,0);
	}
	
	public void setInitVert(myPointf _loc){
		loc.set(_loc);		
	}
	
	//set the location of this vert, and the initial normal and # tris
	public void setVert(myVectorf _norm){
		numTris++;				//increment # of triangles sharing this vertex
		if(numTris == 1){
			n.set(_norm);
		} else {			
			n._add(_norm);
		}
	}

	//draw per vert normal
//	public void drawMe(cs7492Proj3 pa){
//		pa.gl_normal(n);                                         // changes normal for smooth shading		
//		pa.gl_vertex(loc);
//	}
		
}//

