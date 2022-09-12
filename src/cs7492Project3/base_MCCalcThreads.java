package cs7492Project3;

import java.util.ArrayList;
import java.util.concurrent.Callable;


public abstract class base_MCCalcThreads implements Callable<Boolean> {
	private myMCCube[] grid;				//3d grid holding marching cubes
	
	public final base_MarchingCubes MC;
	//vert idx's for comparison along cube edges
	public int[][] vIdx = new int[][]{	{0,1},{1,2},{2,3},{3,0},
										{4,5},{5,6},{6,7},{7,4},
										{0,4},{1,5},{2,6},{3,7}};
	// obj data
	public float isolevel;
	// precalc powers of 2
	public static final int[] pow2 = new int[]{1,2,4,8,16,32,64,128,256,512,1024,2048,4096, 8192, 16384, 32768 };

	public int stIdx;
	public final int endI, endJ;
	
	public ArrayList<myMCTri> triList;
	/**
	 * whether or not to use vertex normals for calculation and rendering
	 */
	private boolean useVertNorms;
	
	public base_MCCalcThreads(base_MarchingCubes _MC, myMCCube[] _grid, int _stIdx, int _gxM1, int _gyM1){
		MC = _MC;
		grid = _grid;
		isolevel = 0.5f;	
		triList = new ArrayList<myMCTri>();
		useVertNorms = false;
		
		endI = _gxM1;
		endJ = _gyM1;
		stIdx = _stIdx;
	}
	
	//Determine the index into the edge table which tells us which vertices are inside of the surface
	private int buildCubeIDX(myMCCube gCube, int modIsoLvl) {
		int cubeIDX = 0;	
		for(int i =0; i<gCube.val.length;++i){if(gCube.val[i] < modIsoLvl){	cubeIDX |= pow2[i];}}
		// Cube is entirely in or out of the surface
		if (myMC_Consts.edgeTable[cubeIDX] == 0) {	return -1;}
		return cubeIDX;
	}
	

	public void toTriangle(myMCCube gCube, int modIsoLvl) {
		int cubeIDX = buildCubeIDX(gCube, modIsoLvl);
		if(cubeIDX == -1) {return;}		
		myPointf[] vertList = new myPointf[16];
		// Find the vertices where the surface intersects the cube
		int idx0, idx1;
		for(int i =0; i<vIdx.length;++i){
			if ((myMC_Consts.edgeTable[cubeIDX] & pow2[i]) != 0){
				idx0 = vIdx[i][0];	idx1 = vIdx[i][1];
				vertList[i] = VertexInterp(gCube.p[idx0], gCube.p[idx1], gCube.val[idx0], gCube.val[idx1], modIsoLvl);
			}
		}
		
		// Create the triangle
		int araIDX = cubeIDX << 4, araIDXpI;
		//myVectorf _loc = new myVectorf(gridI,  gridJ,  gridK);
		myMCTri tmpTri;
	
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			tmpTri = new myMCTri(new myPointf[]{ vertList[myMC_Consts.triAra[araIDXpI]], vertList[myMC_Consts.triAra[araIDXpI + 1]],	vertList[myMC_Consts.triAra[araIDXpI + 2]]});
			triList.add(tmpTri); 
		}
		
	}//toTriangle

	/*
	 * Given a grid cell and an isolevel, calculate the facets required to represent the isosurface through the cell. Return the number
	 * of triangular facets, the lclTris ara will have the verts of =< 5 triangular facets. 0 will be returned if the grid cell
	 * is either totally above of totally below the isolevel.
	 * modIsoLvl is iso level made into bit mask
	 */
	public void toTriangleVertShade(myMCCube gCube, int modIsoLvl) {
		int cubeIDX = buildCubeIDX(gCube, modIsoLvl);
		if(cubeIDX == -1) {return;}		
		
		// Find the vertices where the surface intersects the cube
		int idx0, idx1;
		for(int i =0; i<vIdx.length;++i){
			if ((myMC_Consts.edgeTable[cubeIDX] & pow2[i]) != 0){
				idx0 = vIdx[i][0];	idx1 = vIdx[i][1];
				MC.synchSetVertList(gCube.vIdx[i], VertexInterp(gCube.p[idx0], gCube.p[idx1], gCube.val[idx0], gCube.val[idx1], modIsoLvl));
			}}
		// Create the triangle
		int araIDX = cubeIDX << 4, araIDXpI,gtIDX0, gtIDX1, gtIDX2;
		//myVectorf _loc = new myVectorf(gridI,  gridJ,  gridK);
		myMCTri tmpTri;
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			gtIDX0 = gCube.vIdx[myMC_Consts.triAra[araIDXpI]];
			gtIDX1 = gCube.vIdx[myMC_Consts.triAra[araIDXpI + 1]];
			gtIDX2 = gCube.vIdx[myMC_Consts.triAra[araIDXpI + 2]];
			
			tmpTri = new myMCTri(new myMCVert[]{MC.usedVertList.get(gtIDX0),MC.usedVertList.get(gtIDX1),MC.usedVertList.get(gtIDX2) });
			triList.add(tmpTri); 
		}     
	}//toTriangleVertShade

	/* 
	 * Linearly interpolate the position where an isosurface cuts an edge between two vertices, each with their own scalar value.
	 * Bounds check isolevel
	 */
	public myVectorf VertexInterp(myVectorf p1, myVectorf p2, float valp1, float valp2, float valPt) {
		return (new myVectorf(p1,((valPt - valp1) / (valp2 - valp1)),p2));
	}
	
	public void setSimVals(boolean useVerts, float _isoLvl) {
		isolevel=_isoLvl;
		useVertNorms=useVerts;
	}
	
	protected final void setCubeVals(myMCCube cube, int mask) {
		for (int id=0; id<8;++id) {			//each of 8 verts on grid
			//grid[idx].val[id]= ((intData[grid[idx].dataPIdx[id]] >> disp & 0xFF));///256.0f);
			cube.val[id]= (MC.intData[cube.dataPIdx[id]] & mask);
		}
	}
	
	protected abstract int getModIsoLevel();
	/**
	 * if masking array of data is necessary, build mask here.  otherwise, set to 0xFFFFFFFF/-1;
	 * @return
	 */
	protected abstract int getDataMask();
	//protected abstract int 
	@Override
	public Boolean call() throws Exception {
		triList.clear();
		//instead of modifying each data value by shifting, masking and division, can we multiply and shift the iso level - 1 time mult + shift instead of many shift/divs
		int idx = stIdx;
		int modIsoLvl = getModIsoLevel();
		int mask = getDataMask();//0xFF << disp;			//mask is necessary, both u and v results returned simultaneously
		
		if(useVertNorms) {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					setCubeVals(grid[idx], mask);
					toTriangleVertShade(grid[idx], modIsoLvl);
					++idx;
				}
			}		
			
		} else {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					setCubeVals(grid[idx], mask);
					toTriangle(grid[idx], modIsoLvl);
					++idx;
				}
			}	
		}
		
		synchronized (MC.triList) {
			MC.triList.addAll(triList);
		}		
		return true;
	}//call
	
}//class def
	

