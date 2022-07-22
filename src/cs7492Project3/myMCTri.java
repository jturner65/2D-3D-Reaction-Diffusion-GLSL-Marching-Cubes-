package cs7492Project3;

public class myMCTri {	
	public myPointf pt[] = new myPointf[3];
	public myMCVert verts[] = new myMCVert[3];
	public myVectorf n;
	public myMCTri(myPointf[] pts) {
		for (int i = 0; i < 3; ++i) {
			pt[i] = new myPointf(pts[i]);
		} 
		//n = new myVectorf(pt[0],pt[1])._cross(new myVectorf(pt[0],pt[2]));
	}
	public myMCTri( myMCVert[] _v) {
		for (int i = 0; i < 3; ++i) {
			verts[i]=_v[i];
			pt[i] = new myPointf(verts[i].loc);
		} 
		n = new myVectorf(pt[0],pt[1])._cross(new myVectorf(pt[0],pt[2]));
		if(n.sqMagn < .000001f){
			n = new myVectorf(pt[0],pt[2])._cross(new myVectorf(pt[2],pt[1]));
		}
		n._normalize();//	
		for(int i =0; i<3; ++i){
			verts[i].setVert(n);
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
			pa.gl_normal(verts[i].n._normalize()); 
			pa.gl_vertex(pt[i]);
		}		
	}
}//myMCTri