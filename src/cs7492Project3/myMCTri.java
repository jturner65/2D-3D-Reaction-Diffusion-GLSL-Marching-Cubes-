package cs7492Project3;

public class myMCTri {	
	private myPointf pt[] = new myPointf[] {
			new myPointf(),new myPointf(),new myPointf()
	};
	public myMCVert verts[] = new myMCVert[3];
	public myMCTri(myPointf[] pts) {
		for (int i = 0; i < 3; ++i) {
			pt[i].set(pts[i]);
		} 
	}
	
	public myMCTri( myMCVert[] _v) {
		for (int i = 0; i < 3; ++i) {
			verts[i]=_v[i];
			pt[i].set(verts[i].loc);
		}
	}

	public void drawMe(cs7492Proj3 pa){
		pa.gl_vertex(pt[0]);
		pa.gl_vertex(pt[1]);
		pa.gl_vertex(pt[2]);
	}
	//draw via verts with per-vertex normals
	public void drawMeVerts(cs7492Proj3 pa){
		for(int i =0; i<3; ++i){
			pa.gl_normal(verts[i].n); 
			pa.gl_vertex(pt[i]);
		}		
	}
}//myMCTri