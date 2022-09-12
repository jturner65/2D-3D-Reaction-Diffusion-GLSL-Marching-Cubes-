package cs7492Project3;

public class myMCCube {
	public int idx;
	public static int gx = 0,gxgy = 0;
	private myPointf[] p = new myPointf[] {
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf(),
			new myPointf()};
	public int[] dataPIdx = new int[8];		//idx in data corresponding to each point in cube
	public float[] val = new float[] {0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f,0.0f};
	public int[] vIdx;// = new int[12];//1/2 way between corners along edges - 2*dim -1 along each dimension

	public myMCCube(int i, int j, int k, int _idx, myPointf datStep){
		idx = _idx;
		int i1 = i+1, 
			j1 = j+1, 
			k1 = k+1; 
		int	jgx = j*gx, 
			jgx1 = j1*gx;
		int kgxgy = k*gxgy, 
			k1gxgy = k1 * gxgy;
		dataPIdx[0] =  i  + jgx  + kgxgy;
		dataPIdx[1] =  i1 + jgx  + kgxgy;
		dataPIdx[2] =  i1 + jgx1 + kgxgy;
		dataPIdx[3] =  i  + jgx1 + kgxgy;
		
		dataPIdx[4] =  i  + jgx  + k1gxgy;
		dataPIdx[5] =  i1 + jgx  + k1gxgy;
		dataPIdx[6] =  i1 + jgx1 + k1gxgy;
		dataPIdx[7] =  i  + jgx1 + k1gxgy;

		//vertex locations, where we split each grid cube into 8 cubes(i.e. halving each side)
		//in a pure vertex ara these are the locations of the 12 vertices that
		//follow this pattern
		//{{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
		
		vIdx = new int[] {
				dataPIdx[0] + dataPIdx[1],
				dataPIdx[1] + dataPIdx[2],
				dataPIdx[2] + dataPIdx[3],
				dataPIdx[3] + dataPIdx[0],
				
				dataPIdx[4] + dataPIdx[5],
				dataPIdx[5] + dataPIdx[6],
				dataPIdx[6] + dataPIdx[7],
				dataPIdx[7] + dataPIdx[4],	
				
				dataPIdx[0] + dataPIdx[4], 
				dataPIdx[1] + dataPIdx[5], 
				dataPIdx[2] + dataPIdx[6], 
				dataPIdx[3] + dataPIdx[7]
		};
		float iSt = i * datStep.x, jSt = j * datStep.y, kSt = k * datStep.z;
		float i1St = i1 * datStep.x, j1St = j1 * datStep.y, k1St = k1 * datStep.z;
		
		p[0].set(iSt, jSt, kSt);
		p[1].set(i1St,jSt,kSt);
		p[2].set(i1St,j1St,kSt);
		p[3].set(iSt, j1St,kSt);
		p[4].set(iSt,jSt,k1St);
		p[5].set(i1St,jSt,k1St);
		p[6].set(i1St,j1St,k1St);
		p[7].set(iSt,j1St,k1St);
		
	}
	/**
	 * Linearly interpolate the position where an isosurface cuts an edge between two vertices, each with their own scalar value. Bounds check isolevel
	 * 
	 * @param idx0 index in point array/interpolant array for first point
	 * @param idx1 index in point array/interpolant array for 2nd point
	 * @param valPt interpolant representing isosurface value, between val[idx1] and val[idx0]
	 * @return
	 */
	public myPointf VertexInterp(int idx0, int idx1, float valPt) {
		return (new myPointf(p[idx0],((valPt - val[idx0]) / (val[idx1] - val[idx0])),p[idx1]));
	}


}//class myMCCube