package cs7492Project3;

import java.util.ArrayList;
import java.util.concurrent.Callable;


public abstract class base_MCCalcThreads implements Callable<Boolean> {
	/**
	 * 3d grid holding marching cubes
	 */
	protected myMCCube[] grid;	
	
	public final base_MarchingCubes MC;

	/**
	 * Concentration level to determine iso surface to display
	 */
	public float isolevel;
	/**
	 *  precalc powers of 2
	 */
	public static final int[] pow2 = new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};

	public int stIdx;
	public final int endI, endJ;
	
	public ArrayList<myMCTri> triList;
	/**
	 * whether or not to use vertex normals for calculation and rendering
	 */
	protected boolean useVertNorms;
	
	public base_MCCalcThreads(base_MarchingCubes _MC, myMCCube[] _grid, int _stIdx, int _gxM1, int _gyM1){
		MC = _MC;
		grid = _grid;	
		triList = new ArrayList<myMCTri>();
		setSimVals(false, 0.5f);
		
		endI = _gxM1;
		endJ = _gyM1;
		stIdx = _stIdx;
	}
	
	/**
	 * Determine the index into the edge table which tells us which vertices are inside of the surface
	 * @param gCube
	 * @param modIsoLvl
	 */
	private void buildCubeIDX(myMCCube gCube, int modIsoLvl) {
		int cubeIDX = 0;	
		for(int i =0; i<gCube.val.length;++i){if(gCube.val[i] < modIsoLvl){	cubeIDX |= pow2[i];}}
		// Cube is entirely in or out of the surface
		if ((myMC_Consts.edgeTable[cubeIDX] == 0) || (myMC_Consts.edgeTable[cubeIDX] == 255))  {	gCube.edgeAraIDX = -1;}
		gCube.edgeAraIDX = cubeIDX;
	}
	
	/**
	 * Build triangles for face-shaded surface based on isosurface lvl threshold
	 * @param gCube cube being worked on
	 * @param modIsoLvl isosurface lvl threshold
	 */
	private void toTriangle(myMCCube gCube, int modIsoLvl) {
		if(gCube.edgeAraIDX == -1) {return;}		
		//holding array for verts to use
		myMCVert[] vertList = new myMCVert[myMCCube.edgeVertIDXs.length];
		// Find the vertices where the surface intersects the cube
		for(int i =0; i<myMCCube.edgeVertIDXs.length;++i){
			if ((myMC_Consts.edgeTable[gCube.edgeAraIDX] & pow2[i]) != 0){
				vertList[i] = gCube.VertexInterp(myMCCube.edgeVertIDXs[i][0], myMCCube.edgeVertIDXs[i][1], modIsoLvl);
			}
		}
		// Create the triangle
		int araIDX = gCube.edgeAraIDX << 4, araIDXpI;
		myMCTri tmpTri;	
		//loop through by 3s - each triangle
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			tmpTri = new myMCTri(new myMCVert[]{ 
							vertList[myMC_Consts.triAra[araIDXpI]], 
							vertList[myMC_Consts.triAra[araIDXpI + 1]],	
							vertList[myMC_Consts.triAra[araIDXpI + 2]]});
			triList.add(tmpTri); 
		}		
	}//toTriangle
	
	/**
	 * Aggregate all surface vertices into single list, so adjacent faces can contribute their normals.
	 * @param gCube
	 * @param modIsoLvl
	 */
	private void buildSyncList(myMCCube gCube, int modIsoLvl) {
		if(gCube.edgeAraIDX == -1) {return;}				
		// Find the vertices where the surface intersects the cube
		for(int i =0; i<myMCCube.edgeVertIDXs.length;++i){
			if ((myMC_Consts.edgeTable[gCube.edgeAraIDX] & pow2[i]) != 0){
				myMCVert vert = gCube.VertexInterp(myMCCube.edgeVertIDXs[i][0], myMCCube.edgeVertIDXs[i][1], modIsoLvl);
				MC.synchSetVertList(gCube.edgeGlblVertIDXs[i], vert);
			} 
		} 
	}//buildSyncList
	
	/**
	 * Build triangles for vertex-shaded surface based on isosurface lvl threshold
	 * @param gCube cube being worked on
	 * @param modIsoLvl isosurface lvl threshold
	 */
	private void toTriangleVertShade(myMCCube gCube, int modIsoLvl) {
		if(gCube.edgeAraIDX == -1) {return;}
		// Create the triangle
		//shift cube IDX by 4 bits since we use a single array for the triangle edge map
		int araIDX = gCube.edgeAraIDX << 4, araIDXpI;
		myMCTri tmpTri;
		//loop through by 3s - each triangle
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			myMCVert[] triVertList = MC.synchGetVertList(gCube, araIDXpI);
			//List is null if any 2 verts are equal - degenerate triangle
			if (triVertList != null) {
				tmpTri = new myMCTri(triVertList);
				triList.add(tmpTri);
			}
		}     
	}//toTriangleVertShade
	
	public void setSimVals(boolean useVerts, float _isoLvl) {
		isolevel=_isoLvl;
		useVertNorms=useVerts;
	}
	
	/**
	 * Sets the iso surface values of each vertex of the cube based on the data from the shader
	 * @param cube a component of the MC cube grid
	 * @param mask Masks the channel to use for the concentration
	 * @param modIsoLvl threshold for iso surface
	 */
	protected final void setCubeVals(myMCCube cube, int mask, int modIsoLvl) {
		for (int id=0; id<8;++id) {			//each of 8 verts on grid
			cube.val[id]= (MC.intData[cube.dataPIdx[id]] & mask);
		}
		//build and set cube index
		buildCubeIDX(cube, modIsoLvl);
	}// setCubeVals()
	
	protected abstract int getModIsoLevel();
	/**
	 * if masking array of data is necessary, build mask here.  otherwise, set to 0xFFFFFFFF/-1;
	 * @return
	 */
	protected abstract int getDataMask();
	
	/**
	 * Set iso surface-driven values for every cube in this callable's slice of the grid
	 */
	protected void setAllCubeVals() {
		int idx = stIdx;
		int modIsoLvl = getModIsoLevel();	
		int mask = getDataMask();//0xFF << disp;			//mask is necessary, both u and v results returned simultaneously, mask filters
		for(int j = 0; j < endJ; ++j){
			for (int i = 0; i < endI; ++i) {
				setCubeVals(grid[idx], mask, modIsoLvl);
				++idx;
			}
		}
	}// setAllCubeVals()
	
	/**
	 * Build the list of vertices of the iso surface that are 
	 * present in the cubes of this slice of the grid.  Used for vertex normals
	 */
	protected void buildSyncVertList() {
		//currently only use MC.usedVertList for vertex normal shading
		if(useVertNorms) {
			int modIsoLvl = getModIsoLevel();		
			int idx = stIdx;
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					buildSyncList(grid[idx], modIsoLvl);
					++idx;
				}
			}
		}
	}// buildSyncVertList()
	
	/**
	 * Build the iso surface's constituent triangles.
	 */
	protected void buildTriangles() {
		int modIsoLvl = getModIsoLevel();		
		int idx = stIdx;
		if(useVertNorms) {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					toTriangleVertShade(grid[idx], modIsoLvl);
					++idx;
				}
			}		
		} else {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					toTriangle(grid[idx], modIsoLvl);
					++idx;
				}
			}	
		}	
	}// buildTriangles()
	
	protected void mergeAllTris() {
		synchronized (MC.triList) {			MC.triList.addAll(triList);		}
		triList.clear();
	}
	
	protected int func = 0;
	public void setFunction(int _func) {
		//func == 0 : set cube values
		//func == 1 : build sync list for vert shaded, nothing for face shaded
		//func == 2 : build triangles
		//func == 3 : merge triangles to list
		if ((_func >=0) && (_func <=3)){
			func = _func;
		}
	}	
	
	@Override
	public Boolean call() throws Exception {
		switch (func) {
			case 0 : { setAllCubeVals(); return true;}
			case 1 : { buildSyncVertList(); return true;}
			case 2 : { buildTriangles(); return true;}
			case 3 : { mergeAllTris(); return true;}
			default : return false;
		}
	}//call
	
}//class def
	

