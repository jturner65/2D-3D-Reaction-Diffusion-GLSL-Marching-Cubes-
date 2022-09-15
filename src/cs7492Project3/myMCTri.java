package cs7492Project3;

public class myMCTri {
	public myMCVert verts[] = new myMCVert[3];
	
	public myMCTri(myMCVert[] _v) {
		for (int i = 0; i < 3; ++i) {verts[i]=_v[i];}
	}
	
	//draw with implied face normal	
	public void drawMe(cs7492Proj3 pa){
		for(int i =0; i<3; ++i){
			pa.gl_vertex(verts[i]);
		}		
	}
	
	//draw via verts with per-vertex normals
	public void drawMeVerts(cs7492Proj3 pa){
		for(int i =0; i<3; ++i){
			pa.gl_normal(verts[i].n); 
			pa.gl_vertex(verts[i]);
		}		
	}
	
	//draw with implied face normal
	public void drawMe_Color(cs7492Proj3 pa){
		for(int i =0; i<3; ++i){
			pa.setFill((int)verts[i].clr.x, (int)verts[i].clr.y, (int)verts[i].clr.z);
			pa.gl_vertex(verts[i]);
		}	
	}
	
	//draw via verts with per-vertex normals
	public void drawMeVerts_Color(cs7492Proj3 pa){
		for(int i =0; i<3; ++i){
			pa.gl_normal(verts[i].n); 
			pa.setFill((int)verts[i].clr.x, (int)verts[i].clr.y, (int)verts[i].clr.z);
			pa.gl_vertex(verts[i]);
		}		
	}

	
}//myMCTri