package cs7492Project3;

import java.nio.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Future;

import processing.core.*;
import processing.opengl.*;

public class myMarchingCubes {
	public cs7492Proj3 p;

	// obj data
	public float isolevel;

	public PGL pgl;
	public PShader sh;

	public int gx, gy, gz, 
		vgx, vgy, vgz,
		numCubes, gxgy, vgxgy;
	
	public FloatBuffer dataBuf;

	public float minVal, maxVal;
	
	public List<myMCTri> triList = Collections.synchronizedList(new ArrayList<myMCTri>());

	private myMCCube[] grid;				//3d grid holding marching cubes
	
	public List<Future<Boolean>> callMCCalcFutures;
	public List<myMCCalcThreads> callMCCalcs;
//	//structure to hold mid-edge vertices
	public ConcurrentSkipListMap<Integer, myMCVert> vertList, usedVertList;

	// draw data
	public myVector dataStep;
	public ByteBuffer buf;
	public int cellSize;			//how big are the cells in the resultant grid
	//holding ara
	public int[] intData;
	
	public myMarchingCubes(cs7492Proj3 _p, int _cs) {
		p = _p;
		cellSize = _cs;
		
		isolevel = 0.5f;
		
		callMCCalcs = new ArrayList<myMCCalcThreads>();
		callMCCalcFutures = new ArrayList<Future<Boolean>>(); 
		
		setDimAndRes(p.gridDimX, p.gridDimY, p.gridDimZ, (int)(p.gridDimX/cellSize), (int)(p.gridDimY/cellSize), (int)(p.gridDimZ/cellSize));
		//dataToTris();
	}
	//setup dimensions of resultant space, and dimensions of data array
	//sx, sy and sz are pixel dimensions,
	//x,y,z are cell dimensions -> # of grid cells in each dimension
	public void setDimAndRes(float sx, float sy, float sz,int x, int y, int z) {
		gx = x;	gy = y;	gz = z;
		gxgy = x * y;
		vgx = (x*2)-1;
		vgy = (y*2)-1;
		vgz = (z*2)-1;
		numCubes = x * y * z;

		triList = new ArrayList<myMCTri>();		
		dataStep = new myVector(cellSize, cellSize, cellSize);
		myMCCube.gx = gx;
		myMCCube.gxgy = gxgy;
		grid = new myMCCube[numCubes];			//is a global grid faster?
		intData = new int[numCubes];
				
		for (int k = 0; k < gz - 1; ++k) {
			int stIdx = IX(0,0,k), endIdx = IX(-gxgy,0,k+1),idx = IX(0,0,k);	
			for (int j = 0; j < gy - 1; ++j) {		
				for (int i = 0; i < gx - 1; ++i) {									
					grid[idx] = new myMCCube(i, j, k,  idx, dataStep);					
					idx++;
				}
			}
			callMCCalcs.add(new myMCCalcThreads(this, grid, stIdx, endIdx, isolevel, k, gx - 1, gy - 1));	//process 2d grid for each thread
		}
	}
	
	public void synchSetVertList(int idx, myPointf _loc){
		synchronized(usedVertList){
			myMCVert tmp = vertList.get(idx);
			tmp.setInitVert(_loc);
			usedVertList.put(idx, tmp);
		}
	}
	
	
	//idxing into 3d grid should be for z -> for y -> for x (inner)
	public int IX(int x, int y, int z){return (x + (y * gy) + (z * gxgy));}
	public void copyColorAraToData(int[] clrPxl){
		int idx = 0, j_kgxgy;
		isolevel =  p.flags[p.dispChemU] ?  p.guiObjs[p.gIDX_isoLvl].valAsFloat() : 1.0f - p.guiObjs[p.gIDX_isoLvl].valAsFloat();
		myMCCalcThreads.disp = (p.flags[p.dispChemU]) ? 16 : 8;
		for (int j = 0; j < gy; ++j){for (int k = 0; k < numCubes; k+=gxgy){j_kgxgy = j + k; for (int i = 0; i < gx; ++i)  {
			intData[j_kgxgy]= clrPxl[idx++];
			j_kgxgy+=gx;
		}}}			

		//int callIdx = 0;
		myMCCalcThreads.isolevel = isolevel;
//		for(myMCVert v : usedVertList.values()){v.clearVert();}
//		usedVertList.clear();
		try {callMCCalcFutures = p.th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
	}
	
	
	public Iterator<myMCTri> i;
	private void drawVNorms(){
		p.beginShape(p.TRIANGLES);		
		synchronized(triList) {
			i = triList.iterator(); // Must be in synchronized block		    
		    while (i.hasNext()){
		    	i.next().drawMeVerts(p);
		    }
		  }
		p.endShape();	
		triList = new ArrayList<myMCTri>();
	}
	public void draw() {
		p.setColorValFill(p.triColors[p.RD.dispChem][p.currDispType]);	
		p.noStroke();
        if (p.flags[p.useVertNorms]){
        	 drawVNorms();
        } else {
			p.beginShape(p.TRIANGLES);
			
			synchronized(triList) {
				i = triList.iterator(); // Must be in synchronized block		    
			    while (i.hasNext()){
			    	i.next().drawMe(p);
			    }
			  }
			p.endShape();	
			triList = new ArrayList<myMCTri>();
        }
	}

	public String toString() {
		String res  ="";// "tris: " + ntri + " volume: " + gx + " " + gy+ " " + gz + " cells: " + numCubes + " iso: " + isolevel;
		return res;
	}
	
	public FloatBuffer allocateDirectFloatBuffer(int n) {
		  return ByteBuffer.allocateDirect(n * Float.SIZE/8).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}
	// edge and triangle tables (condensed to 1d ara) - data from http://paulbourke.net/geometry/polygonise/ 
	int edgeTable[] = {
	0x0,   0x109, 0x203, 0x30a, 0x406, 0x50f, 0x605, 0x70c, 0x80c, 0x905, 0xa0f, 0xb06, 0xc0a, 0xd03, 0xe09, 0xf00, 
	0x190, 0x99,  0x393, 0x29a, 0x596, 0x49f, 0x795, 0x69c, 0x99c, 0x895, 0xb9f, 0xa96, 0xd9a, 0xc93, 0xf99, 0xe90, 
	0x230, 0x339, 0x33,  0x13a, 0x636, 0x73f, 0x435, 0x53c, 0xa3c, 0xb35, 0x83f, 0x936, 0xe3a, 0xf33, 0xc39, 0xd30, 
	0x3a0, 0x2a9, 0x1a3, 0xaa,  0x7a6, 0x6af, 0x5a5, 0x4ac, 0xbac, 0xaa5, 0x9af, 0x8a6, 0xfaa, 0xea3, 0xda9, 0xca0, 
	0x460, 0x569, 0x663, 0x76a, 0x66,  0x16f, 0x265, 0x36c, 0xc6c, 0xd65, 0xe6f, 0xf66, 0x86a, 0x963, 0xa69, 0xb60, 
	0x5f0, 0x4f9, 0x7f3, 0x6fa, 0x1f6, 0xff,  0x3f5, 0x2fc, 0xdfc, 0xcf5, 0xfff, 0xef6, 0x9fa, 0x8f3, 0xbf9, 0xaf0, 
	0x650, 0x759, 0x453, 0x55a, 0x256, 0x35f, 0x55,  0x15c, 0xe5c, 0xf55, 0xc5f, 0xd56, 0xa5a, 0xb53, 0x859, 0x950, 
	0x7c0, 0x6c9, 0x5c3, 0x4ca, 0x3c6, 0x2cf, 0x1c5, 0xcc,  0xfcc, 0xec5, 0xdcf, 0xcc6, 0xbca, 0xac3, 0x9c9, 0x8c0,
	0x8c0, 0x9c9, 0xac3, 0xbca, 0xcc6, 0xdcf, 0xec5, 0xfcc, 0xcc,  0x1c5, 0x2cf, 0x3c6, 0x4ca, 0x5c3, 0x6c9, 0x7c0, 
	0x950, 0x859, 0xb53, 0xa5a, 0xd56, 0xc5f, 0xf55, 0xe5c, 0x15c, 0x55,  0x35f, 0x256, 0x55a, 0x453, 0x759, 0x650, 
	0xaf0, 0xbf9, 0x8f3, 0x9fa, 0xef6, 0xfff, 0xcf5, 0xdfc, 0x2fc, 0x3f5, 0xff,  0x1f6, 0x6fa, 0x7f3, 0x4f9, 0x5f0, 
	0xb60, 0xa69, 0x963, 0x86a, 0xf66, 0xe6f, 0xd65, 0xc6c, 0x36c, 0x265, 0x16f, 0x66,  0x76a, 0x663, 0x569, 0x460, 
	0xca0, 0xda9, 0xea3, 0xfaa, 0x8a6, 0x9af, 0xaa5, 0xbac, 0x4ac, 0x5a5, 0x6af, 0x7a6, 0xaa,  0x1a3, 0x2a9, 0x3a0, 
	0xd30, 0xc39, 0xf33, 0xe3a, 0x936, 0x83f, 0xb35, 0xa3c, 0x53c, 0x435, 0x73f, 0x636, 0x13a, 0x33,  0x339, 0x230, 
	0xe90, 0xf99, 0xc93, 0xd9a, 0xa96, 0xb9f, 0x895, 0x99c, 0x69c, 0x795, 0x49f, 0x596, 0x29a, 0x393, 0x99,  0x190, 
	0xf00, 0xe09, 0xd03, 0xc0a, 0xb06, 0xa0f, 0x905, 0x80c, 0x70c, 0x605, 0x50f, 0x406, 0x30a, 0x203, 0x109, 0x0 };

	int triAra[] = // 256x16 as 1-d array
	{-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,8,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,1,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,8,3,9,8,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	1,2,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,8,3,1,2,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,9,2,10,0,2,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,2,8,3,2,10,8,10,9,8,-1,-1,-1,-1,-1,-1,-1,
	3,11,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,11,2,8,11,0,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,9,0,2,3,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,11,2,1,9,11,9,8,11,-1,-1,-1,-1,-1,-1,-1,
	3,10,1,11,10,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,10,1,0,8,10,8,11,10,-1,-1,-1,-1,-1,-1,-1,3,9,0,3,11,9,11,10,9,-1,-1,-1,-1,-1,-1,-1,9,8,10,10,8,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	4,7,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,4,3,0,7,3,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,1,9,8,4,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,4,1,9,4,7,1,7,3,1,-1,-1,-1,-1,-1,-1,-1,
	1,2,10,8,4,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,4,7,3,0,4,1,2,10,-1,-1,-1,-1,-1,-1,-1,9,2,10,9,0,2,8,4,7,-1,-1,-1,-1,-1,-1,-1,2,10,9,2,9,7,2,7,3,7,9,4,-1,-1,-1,-1,
	8,4,7,3,11,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,11,4,7,11,2,4,2,0,4,-1,-1,-1,-1,-1,-1,-1,9,0,1,8,4,7,2,3,11,-1,-1,-1,-1,-1,-1,-1,4,7,11,9,4,11,9,11,2,9,2,1,-1,-1,-1,-1,
	3,10,1,3,11,10,7,8,4,-1,-1,-1,-1,-1,-1,-1,1,11,10,1,4,11,1,0,4,7,11,4,-1,-1,-1,-1,4,7,8,9,0,11,9,11,10,11,0,3,-1,-1,-1,-1,4,7,11,4,11,9,9,11,10,-1,-1,-1,-1,-1,-1,-1,
	9,5,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,9,5,4,0,8,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,5,4,1,5,0,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,8,5,4,8,3,5,3,1,5,-1,-1,-1,-1,-1,-1,-1,
	1,2,10,9,5,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,0,8,1,2,10,4,9,5,-1,-1,-1,-1,-1,-1,-1,5,2,10,5,4,2,4,0,2,-1,-1,-1,-1,-1,-1,-1,2,10,5,3,2,5,3,5,4,3,4,8,-1,-1,-1,-1,
	9,5,4,2,3,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,11,2,0,8,11,4,9,5,-1,-1,-1,-1,-1,-1,-1,0,5,4,0,1,5,2,3,11,-1,-1,-1,-1,-1,-1,-1,2,1,5,2,5,8,2,8,11,4,8,5,-1,-1,-1,-1,
	10,3,11,10,1,3,9,5,4,-1,-1,-1,-1,-1,-1,-1,4,9,5,0,8,1,8,10,1,8,11,10,-1,-1,-1,-1,5,4,0,5,0,11,5,11,10,11,0,3,-1,-1,-1,-1,5,4,8,5,8,10,10,8,11,-1,-1,-1,-1,-1,-1,-1,
	9,7,8,5,7,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,9,3,0,9,5,3,5,7,3,-1,-1,-1,-1,-1,-1,-1,0,7,8,0,1,7,1,5,7,-1,-1,-1,-1,-1,-1,-1,1,5,3,3,5,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	9,7,8,9,5,7,10,1,2,-1,-1,-1,-1,-1,-1,-1,10,1,2,9,5,0,5,3,0,5,7,3,-1,-1,-1,-1,8,0,2,8,2,5,8,5,7,10,5,2,-1,-1,-1,-1,2,10,5,2,5,3,3,5,7,-1,-1,-1,-1,-1,-1,-1,
	7,9,5,7,8,9,3,11,2,-1,-1,-1,-1,-1,-1,-1,9,5,7,9,7,2,9,2,0,2,7,11,-1,-1,-1,-1,2,3,11,0,1,8,1,7,8,1,5,7,-1,-1,-1,-1,11,2,1,11,1,7,7,1,5,-1,-1,-1,-1,-1,-1,-1,
	9,5,8,8,5,7,10,1,3,10,3,11,-1,-1,-1,-1,5,7,0,5,0,9,7,11,0,1,0,10,11,10,0,-1,11,10,0,11,0,3,10,5,0,8,0,7,5,7,0,-1,11,10,5,7,11,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	10,6,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,8,3,5,10,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,9,0,1,5,10,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,8,3,1,9,8,5,10,6,-1,-1,-1,-1,-1,-1,-1,
	1,6,5,2,6,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,6,5,1,2,6,3,0,8,-1,-1,-1,-1,-1,-1,-1,9,6,5,9,0,6,0,2,6,-1,-1,-1,-1,-1,-1,-1,5,9,8,5,8,2,5,2,6,3,2,8,-1,-1,-1,-1,
	2,3,11,10,6,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,11,0,8,11,2,0,10,6,5,-1,-1,-1,-1,-1,-1,-1,0,1,9,2,3,11,5,10,6,-1,-1,-1,-1,-1,-1,-1,5,10,6,1,9,2,9,11,2,9,8,11,-1,-1,-1,-1,
	6,3,11,6,5,3,5,1,3,-1,-1,-1,-1,-1,-1,-1,0,8,11,0,11,5,0,5,1,5,11,6,-1,-1,-1,-1,3,11,6,0,3,6,0,6,5,0,5,9,-1,-1,-1,-1,6,5,9,6,9,11,11,9,8,-1,-1,-1,-1,-1,-1,-1,
	5,10,6,4,7,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,4,3,0,4,7,3,6,5,10,-1,-1,-1,-1,-1,-1,-1,1,9,0,5,10,6,8,4,7,-1,-1,-1,-1,-1,-1,-1,10,6,5,1,9,7,1,7,3,7,9,4,-1,-1,-1,-1,
	6,1,2,6,5,1,4,7,8,-1,-1,-1,-1,-1,-1,-1,1,2,5,5,2,6,3,0,4,3,4,7,-1,-1,-1,-1,8,4,7,9,0,5,0,6,5,0,2,6,-1,-1,-1,-1,7,3,9,7,9,4,3,2,9,5,9,6,2,6,9,-1,
	3,11,2,7,8,4,10,6,5,-1,-1,-1,-1,-1,-1,-1,5,10,6,4,7,2,4,2,0,2,7,11,-1,-1,-1,-1,0,1,9,4,7,8,2,3,11,5,10,6,-1,-1,-1,-1,9,2,1,9,11,2,9,4,11,7,11,4,5,10,6,-1,
	8,4,7,3,11,5,3,5,1,5,11,6,-1,-1,-1,-1,5,1,11,5,11,6,1,0,11,7,11,4,0,4,11,-1,0,5,9,0,6,5,0,3,6,11,6,3,8,4,7,-1,6,5,9,6,9,11,4,7,9,7,11,9,-1,-1,-1,-1,
	10,4,9,6,4,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,4,10,6,4,9,10,0,8,3,-1,-1,-1,-1,-1,-1,-1,10,0,1,10,6,0,6,4,0,-1,-1,-1,-1,-1,-1,-1,8,3,1,8,1,6,8,6,4,6,1,10,-1,-1,-1,-1,
	1,4,9,1,2,4,2,6,4,-1,-1,-1,-1,-1,-1,-1,3,0,8,1,2,9,2,4,9,2,6,4,-1,-1,-1,-1,0,2,4,4,2,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,8,3,2,8,2,4,4,2,6,-1,-1,-1,-1,-1,-1,-1,
	10,4,9,10,6,4,11,2,3,-1,-1,-1,-1,-1,-1,-1,0,8,2,2,8,11,4,9,10,4,10,6,-1,-1,-1,-1,3,11,2,0,1,6,0,6,4,6,1,10,-1,-1,-1,-1,6,4,1,6,1,10,4,8,1,2,1,11,8,11,1,-1,
	9,6,4,9,3,6,9,1,3,11,6,3,-1,-1,-1,-1,8,11,1,8,1,0,11,6,1,9,1,4,6,4,1,-1,3,11,6,3,6,0,0,6,4,-1,-1,-1,-1,-1,-1,-1,6,4,8,11,6,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	7,10,6,7,8,10,8,9,10,-1,-1,-1,-1,-1,-1,-1,0,7,3,0,10,7,0,9,10,6,7,10,-1,-1,-1,-1,10,6,7,1,10,7,1,7,8,1,8,0,-1,-1,-1,-1,10,6,7,10,7,1,1,7,3,-1,-1,-1,-1,-1,-1,-1,
	1,2,6,1,6,8,1,8,9,8,6,7,-1,-1,-1,-1,2,6,9,2,9,1,6,7,9,0,9,3,7,3,9,-1,7,8,0,7,0,6,6,0,2,-1,-1,-1,-1,-1,-1,-1,7,3,2,6,7,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	2,3,11,10,6,8,10,8,9,8,6,7,-1,-1,-1,-1,2,0,7,2,7,11,0,9,7,6,7,10,9,10,7,-1,1,8,0,1,7,8,1,10,7,6,7,10,2,3,11,-1,11,2,1,11,1,7,10,6,1,6,7,1,-1,-1,-1,-1,
	8,9,6,8,6,7,9,1,6,11,6,3,1,3,6,-1,0,9,1,11,6,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,7,8,0,7,0,6,3,11,0,11,6,0,-1,-1,-1,-1,7,11,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	7,6,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,0,8,11,7,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,1,9,11,7,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,8,1,9,8,3,1,11,7,6,-1,-1,-1,-1,-1,-1,-1,
	10,1,2,6,11,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,2,10,3,0,8,6,11,7,-1,-1,-1,-1,-1,-1,-1,2,9,0,2,10,9,6,11,7,-1,-1,-1,-1,-1,-1,-1,6,11,7,2,10,3,10,8,3,10,9,8,-1,-1,-1,-1,
	7,2,3,6,2,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,7,0,8,7,6,0,6,2,0,-1,-1,-1,-1,-1,-1,-1,2,7,6,2,3,7,0,1,9,-1,-1,-1,-1,-1,-1,-1,1,6,2,1,8,6,1,9,8,8,7,6,-1,-1,-1,-1,
	10,7,6,10,1,7,1,3,7,-1,-1,-1,-1,-1,-1,-1,10,7,6,1,7,10,1,8,7,1,0,8,-1,-1,-1,-1,0,3,7,0,7,10,0,10,9,6,10,7,-1,-1,-1,-1,7,6,10,7,10,8,8,10,9,-1,-1,-1,-1,-1,-1,-1,
	6,8,4,11,8,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,6,11,3,0,6,0,4,6,-1,-1,-1,-1,-1,-1,-1,8,6,11,8,4,6,9,0,1,-1,-1,-1,-1,-1,-1,-1,9,4,6,9,6,3,9,3,1,11,3,6,-1,-1,-1,-1,
	6,8,4,6,11,8,2,10,1,-1,-1,-1,-1,-1,-1,-1,1,2,10,3,0,11,0,6,11,0,4,6,-1,-1,-1,-1,4,11,8,4,6,11,0,2,9,2,10,9,-1,-1,-1,-1,10,9,3,10,3,2,9,4,3,11,3,6,4,6,3,-1,
	8,2,3,8,4,2,4,6,2,-1,-1,-1,-1,-1,-1,-1,0,4,2,4,6,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,1,9,0,2,3,4,2,4,6,4,3,8,-1,-1,-1,-1,1,9,4,1,4,2,2,4,6,-1,-1,-1,-1,-1,-1,-1,
	8,1,3,8,6,1,8,4,6,6,10,1,-1,-1,-1,-1,10,1,0,10,0,6,6,0,4,-1,-1,-1,-1,-1,-1,-1,4,6,3,4,3,8,6,10,3,0,3,9,10,9,3,-1,10,9,4,6,10,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	4,9,5,7,6,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,8,3,4,9,5,11,7,6,-1,-1,-1,-1,-1,-1,-1,5,0,1,5,4,0,7,6,11,-1,-1,-1,-1,-1,-1,-1,11,7,6,8,3,4,3,5,4,3,1,5,-1,-1,-1,-1,
	9,5,4,10,1,2,7,6,11,-1,-1,-1,-1,-1,-1,-1,6,11,7,1,2,10,0,8,3,4,9,5,-1,-1,-1,-1,7,6,11,5,4,10,4,2,10,4,0,2,-1,-1,-1,-1,3,4,8,3,5,4,3,2,5,10,5,2,11,7,6,-1,
	7,2,3,7,6,2,5,4,9,-1,-1,-1,-1,-1,-1,-1,9,5,4,0,8,6,0,6,2,6,8,7,-1,-1,-1,-1,3,6,2,3,7,6,1,5,0,5,4,0,-1,-1,-1,-1,6,2,8,6,8,7,2,1,8,4,8,5,1,5,8,-1,
	9,5,4,10,1,6,1,7,6,1,3,7,-1,-1,-1,-1,1,6,10,1,7,6,1,0,7,8,7,0,9,5,4,-1,4,0,10,4,10,5,0,3,10,6,10,7,3,7,10,-1,7,6,10,7,10,8,5,4,10,4,8,10,-1,-1,-1,-1,
	6,9,5,6,11,9,11,8,9,-1,-1,-1,-1,-1,-1,-1,3,6,11,0,6,3,0,5,6,0,9,5,-1,-1,-1,-1,0,11,8,0,5,11,0,1,5,5,6,11,-1,-1,-1,-1,6,11,3,6,3,5,5,3,1,-1,-1,-1,-1,-1,-1,-1,
	1,2,10,9,5,11,9,11,8,11,5,6,-1,-1,-1,-1,0,11,3,0,6,11,0,9,6,5,6,9,1,2,10,-1,11,8,5,11,5,6,8,0,5,10,5,2,0,2,5,-1,6,11,3,6,3,5,2,10,3,10,5,3,-1,-1,-1,-1,
	5,8,9,5,2,8,5,6,2,3,8,2,-1,-1,-1,-1,9,5,6,9,6,0,0,6,2,-1,-1,-1,-1,-1,-1,-1,1,5,8,1,8,0,5,6,8,3,8,2,6,2,8,-1,1,5,6,2,1,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	1,3,6,1,6,10,3,8,6,5,6,9,8,9,6,-1,10,1,0,10,0,6,9,5,0,5,6,0,-1,-1,-1,-1,0,3,8,5,6,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,10,5,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	11,5,10,7,5,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,11,5,10,11,7,5,8,3,0,-1,-1,-1,-1,-1,-1,-1,5,11,7,5,10,11,1,9,0,-1,-1,-1,-1,-1,-1,-1,10,7,5,10,11,7,9,8,1,8,3,1,-1,-1,-1,-1,
	11,1,2,11,7,1,7,5,1,-1,-1,-1,-1,-1,-1,-1,0,8,3,1,2,7,1,7,5,7,2,11,-1,-1,-1,-1,9,7,5,9,2,7,9,0,2,2,11,7,-1,-1,-1,-1,7,5,2,7,2,11,5,9,2,3,2,8,9,8,2,-1,
	2,5,10,2,3,5,3,7,5,-1,-1,-1,-1,-1,-1,-1,8,2,0,8,5,2,8,7,5,10,2,5,-1,-1,-1,-1,9,0,1,5,10,3,5,3,7,3,10,2,-1,-1,-1,-1,9,8,2,9,2,1,8,7,2,10,2,5,7,5,2,-1,
	1,3,5,3,7,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,8,7,0,7,1,1,7,5,-1,-1,-1,-1,-1,-1,-1,9,0,3,9,3,5,5,3,7,-1,-1,-1,-1,-1,-1,-1,9,8,7,5,9,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	5,8,4,5,10,8,10,11,8,-1,-1,-1,-1,-1,-1,-1,5,0,4,5,11,0,5,10,11,11,3,0,-1,-1,-1,-1,0,1,9,8,4,10,8,10,11,10,4,5,-1,-1,-1,-1,10,11,4,10,4,5,11,3,4,9,4,1,3,1,4,-1,
	2,5,1,2,8,5,2,11,8,4,5,8,-1,-1,-1,-1,0,4,11,0,11,3,4,5,11,2,11,1,5,1,11,-1,0,2,5,0,5,9,2,11,5,4,5,8,11,8,5,-1,9,4,5,2,11,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	2,5,10,3,5,2,3,4,5,3,8,4,-1,-1,-1,-1,5,10,2,5,2,4,4,2,0,-1,-1,-1,-1,-1,-1,-1,3,10,2,3,5,10,3,8,5,4,5,8,0,1,9,-1,5,10,2,5,2,4,1,9,2,9,4,2,-1,-1,-1,-1,
	8,4,5,8,5,3,3,5,1,-1,-1,-1,-1,-1,-1,-1,0,4,5,1,0,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,8,4,5,8,5,3,9,0,5,0,3,5,-1,-1,-1,-1,9,4,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	4,11,7,4,9,11,9,10,11,-1,-1,-1,-1,-1,-1,-1,0,8,3,4,9,7,9,11,7,9,10,11,-1,-1,-1,-1,1,10,11,1,11,4,1,4,0,7,4,11,-1,-1,-1,-1,3,1,4,3,4,8,1,10,4,7,4,11,10,11,4,-1,
	4,11,7,9,11,4,9,2,11,9,1,2,-1,-1,-1,-1,9,7,4,9,11,7,9,1,11,2,11,1,0,8,3,-1,11,7,4,11,4,2,2,4,0,-1,-1,-1,-1,-1,-1,-1,11,7,4,11,4,2,8,3,4,3,2,4,-1,-1,-1,-1,
	2,9,10,2,7,9,2,3,7,7,4,9,-1,-1,-1,-1,9,10,7,9,7,4,10,2,7,8,7,0,2,0,7,-1,3,7,10,3,10,2,7,4,10,1,10,0,4,0,10,-1,1,10,2,8,7,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	4,9,1,4,1,7,7,1,3,-1,-1,-1,-1,-1,-1,-1,4,9,1,4,1,7,0,8,1,8,7,1,-1,-1,-1,-1,4,0,3,7,4,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,4,8,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	9,10,8,10,11,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,0,9,3,9,11,11,9,10,-1,-1,-1,-1,-1,-1,-1,0,1,10,0,10,8,8,10,11,-1,-1,-1,-1,-1,-1,-1,3,1,10,11,3,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	1,2,11,1,11,9,9,11,8,-1,-1,-1,-1,-1,-1,-1,3,0,9,3,9,11,1,2,9,2,11,9,-1,-1,-1,-1,0,2,11,8,0,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,3,2,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	2,3,8,2,8,10,10,8,9,-1,-1,-1,-1,-1,-1,-1,9,10,2,0,9,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,2,3,8,2,8,10,0,1,8,1,10,8,-1,-1,-1,-1,1,10,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
	1,3,8,9,1,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,9,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,3,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
	
	
}//class def

class myMCCube {
	public int idx;
//	private int cubeIDX, ETcubeIDX, araCBIdx;
	public static int gx = 0,gxgy = 0;
	public myVectorf[] p =new myVectorf[8];
	public int[] dataPIdx = new int[8];		//idx in data corresponding to each point in cube
	public float[] val = new float[8];
	public int[] vIdx;// = new int[12];//1/2 way between corners along edges - 2*dim -1 along each dimension
//	public myMCCube() {
//		idx = 0;
//		for (int i=0; i<8;++i) {
//			p[i] = new myVectorf(); 
//			val[i] = 0.0f;
//		}
//	}
	public myMCCube(int i, int j, int k, int _idx, myVector datStep){
		idx = _idx;
		int j1 = j+1, k1 = k+1, jgx = j*gx, jgx1 = j1*gx, i1 = i+1;
		int kgxgy = k*gxgy, k1gxgy = k1 * gxgy;
		dataPIdx[0] =  i  + jgx  + kgxgy;
		dataPIdx[1] =  i1 + jgx  + kgxgy;
		dataPIdx[2] =  i1 + jgx1 + kgxgy;
		dataPIdx[3] =  i  + jgx1 + kgxgy;
		dataPIdx[4] =  i  + jgx  + k1gxgy;
		dataPIdx[5] =  i1 + jgx  + k1gxgy;
		dataPIdx[6] =  i1 + jgx1 + k1gxgy;
		dataPIdx[7] =  i  + jgx1 + k1gxgy;

		for (int id=0; id<8;++id) {
			p[id] = new myVectorf(); 
			val[id] = 0.0f;
		}

		//vertex locations, where we split each grid cube into 8 cubes(i.e. halving each side)
		//in a pure vertex ara these are the locations of the 12 vertices that
		//follow this pattern
		//{{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
		
		vIdx = new int[] {
				dataPIdx[0] + dataPIdx[1],dataPIdx[1] + dataPIdx[2],dataPIdx[2] + dataPIdx[3],dataPIdx[3] + dataPIdx[0],		                         
				dataPIdx[4] + dataPIdx[5],dataPIdx[5] + dataPIdx[6],dataPIdx[6] + dataPIdx[7],dataPIdx[7] + dataPIdx[4],		      
				dataPIdx[0] + dataPIdx[4], dataPIdx[1] + dataPIdx[5], dataPIdx[2] + dataPIdx[6], dataPIdx[3] + dataPIdx[7]
		};
		
		p[0].set(i * datStep.x, j * datStep.y, k * datStep.z);
		p[1].set(i1 * datStep.x,j * datStep.y,k * datStep.z);
		p[2].set(i1 * datStep.x,j1 * datStep.y,k * datStep.z);
		p[3].set(i * datStep.x, j1 * datStep.y,k * datStep.z);
		p[4].set(i * datStep.x,j * datStep.y,k1 * datStep.z);
		p[5].set(i1 * datStep.x,j * datStep.y,k1 * datStep.z);
		p[6].set(i1 * datStep.x,j1 * datStep.y,k1 * datStep.z);
		p[7].set(i * datStep.x,j1 * datStep.y,k1 * datStep.z);
		
	}
	
	
//	public void setVal(float[] data){
//		for (int id=0; id<8;++id) {
//			val[id]=data[dataPIdx[id]];
//		}
//	}	
//	
//	public void setData(int i, int j, int k, float[] data, myVector datStep){
//		int j1 = j+1, k1 = k+1, i1 = i+1;
//		int kgxgy = k*gxgy, jgx = j*gx, jgx1 = j1*gx, k1gxgy = k1 * gxgy;
//		p[0].set(i * datStep.x, j * datStep.y, k * datStep.z);
//		p[1].set(i1 * datStep.x,j * datStep.y,k * datStep.z);
//		p[2].set(i1 * datStep.x,j1 * datStep.y,k * datStep.z);
//		p[3].set(i * datStep.x, j1 * datStep.y,k * datStep.z);
//		p[4].set(i * datStep.x,j * datStep.y,k1 * datStep.z);
//		p[5].set(i1 * datStep.x,j * datStep.y,k1 * datStep.z);
//		p[6].set(i1 * datStep.x,j1 * datStep.y,k1 * datStep.z);
//		p[7].set(i * datStep.x,j1 * datStep.y,k1 * datStep.z);
//
//		val[0] = data[i  + jgx  + kgxgy];
//		val[1] = data[i1 + jgx  + kgxgy];
//		val[2] = data[i1 + jgx1 + kgxgy];
//		val[3] = data[i  + jgx1 + kgxgy];
//		val[4] = data[i  + jgx  + k1gxgy];
//		val[5] = data[i1 + jgx  + k1gxgy];
//		val[6] = data[i1 + jgx1 + k1gxgy];
//		val[7] = data[i  + jgx1 + k1gxgy];
//	}

}//class myMCCube
class myMCTri {	
	public myPointf pt[] = new myPointf[3];
	public myMCVert verts[] = new myMCVert[3];
	public myVectorf n;
	public myMCTri(myPointf[] pts) {
		for (int i = 0; i < 3; ++i) {
			pt[i] = new myPointf(pts[i]);
		} 
		n = myVectorf._normalize( new myVectorf(pt[1],pt[0])._cross(new myVectorf(pt[2],pt[0])));//
	}
	public myMCTri(myPointf[] pts, myMCVert[] _v) {
		for (int i = 0; i < 3; ++i) {
			pt[i] = new myPointf(pts[i]);
			verts[i]=_v[i];
		} 
		n = myVectorf._normalize( new myVectorf(pt[1],pt[0])._cross(new myVectorf(pt[2],pt[0])));//	
		for(int i =0; i<3; ++i){
			verts[i].setVert( n);
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
			pa.gl_vertex(verts[i].loc);
		}		
	}
}//myMCTri

//class to store vertexes of triangle - will calculate normal based on all triangles that share this vertex
class myMCVert{
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

