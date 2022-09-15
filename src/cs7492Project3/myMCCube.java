package cs7492Project3;

public class myMCCube {
	public int idx;
	//index into triangle array for cube based on iso surface
	public int edgeAraIDX = -1;
	public static int gx = 0,gxgy = 0;
	private final myPointf[] p = new myPointf[] {
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf()};
	//idx in data corresponding to each vertex in cube
	public final int[] dataPIdx = new int[8];
	// vert idxs for each edge of this cube, as a tuple
	// For cube, to be consistent and to match adjacent cubes, these should always be sorted (lower IDX, higher IDX)
	public final Tuple<Integer,Integer>[] edgeGlblVertIDXs;// = new int[12];//1/2 way between corners along edges - 2*dim -1 along each dimension
	
	public float[] val = new float[] {0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f};
	
	public static final int[][] edgeVertIDXs = new int[][]{	
		{0,1},{1,2},{2,3},{3,0},
		{4,5},{5,6},{6,7},{7,4},
		{0,4},{1,5},{2,6},{3,7}};

	@SuppressWarnings("unchecked")
	public myMCCube(int _i, int _j, int _k, int _idx, myPointf _datStep){
		idx = _idx;
		int i1 = _i+1, 
			j1 = _j+1, 
			k1 = _k+1; 
		int	jgx = _j*gx, 
			jgx1 = j1*gx;
		int kgxgy = _k*gxgy, 
			k1gxgy = k1 * gxgy;
		dataPIdx[0] =  _i  + jgx  + kgxgy;
		dataPIdx[1] =  i1 + jgx  + kgxgy;
		dataPIdx[2] =  i1 + jgx1 + kgxgy;
		dataPIdx[3] =  _i  + jgx1 + kgxgy;
		
		dataPIdx[4] =  _i  + jgx  + k1gxgy;
		dataPIdx[5] =  i1 + jgx  + k1gxgy;
		dataPIdx[6] =  i1 + jgx1 + k1gxgy;
		dataPIdx[7] =  _i  + jgx1 + k1gxgy;

		float iSt = _i * _datStep.x, jSt = _j * _datStep.y, kSt = _k * _datStep.z;
		float i1St = i1 * _datStep.x, j1St = j1 * _datStep.y, k1St = k1 * _datStep.z;
		
		p[0].set(iSt, jSt, kSt);
		p[1].set(i1St,jSt,kSt);
		p[2].set(i1St,j1St,kSt);
		p[3].set(iSt, j1St,kSt);
		p[4].set(iSt,jSt,k1St);
		p[5].set(i1St,jSt,k1St);
		p[6].set(i1St,j1St,k1St);
		p[7].set(iSt,j1St,k1St);

		//edge idxs that are shared by adjacent blocks
		//vertex locations, where we split each grid cube into 8 cubes(i.e. halving each side)
		//in a pure vertex ara these are the locations of the 12 vertices that
		//follow the vert-vert pattern for each edge
		
		edgeGlblVertIDXs = new Tuple[12];
		for(int iter=0; iter < edgeGlblVertIDXs.length;++iter) {
			int[] lclIdxs = edgeVertIDXs[iter];
			int valA = dataPIdx[lclIdxs[0]], valB = dataPIdx[lclIdxs[1]];
			edgeGlblVertIDXs[iter] = (valA > valB) ? 
				new Tuple<Integer,Integer>(valB, valA) :  
				new Tuple<Integer,Integer>(valA, valB);			
		}		
	}
	
	/**
	 * Linearly interpolate the position where an isosurface cuts an edge between two vertices, each with their own scalar value. Bounds check isolevel
	 * 
	 * @param idx0 index in point array/interpolant array for first point
	 * @param idx1 index in point array/interpolant array for 2nd point
	 * @param valPt interpolant representing isosurface value, between val[idx1] and val[idx0]
	 * @return
	 */
	public myMCVert VertexInterp(int idx0, int idx1, float valPt) {
		return (new myMCVert(p[idx0],((valPt - val[idx0]) / (val[idx1] - val[idx0])),p[idx1]));
	}


}//class myMCCube