package cs7492Project3;

import processing.core.*;
import processing.opengl.*;

public class my3DGLSLSolver {
	public cs7492Proj3 p;
	public myRDSolver rd;
	
	//public PImage resImageU, resImageV, redMask, greenMask;//, resDataImage              
	public PGraphics shdrBuf3D;//, shdrBufMC; 			// Image drawn onto by & re-fed to the shader every loop
	public PShader RD3Dshader;//, MCShader;
	// Set the speed of the reaction (number of loops for each frame)
	public int numShdrIters = 5;
	
	public final int cell3dSize;
	public float maxRad, maxDiam,pageSiz, gridDistZ;
	
	public final int gridPxlW3D, gridPxlH3D;
	public int gridX ,gridY ,gridZ;

	public final int seedSizeSq = 10*10;			//seed vals to initialze grid 
	
	public base_MarchingCubes MC;
	public float deltaT;
	//PImage tmp;

	// sim values that will be passed to the shader
	private float ru, rv, k, f;
	//pass dimensions of cell grid - use 1 pixel in shader image per gridcell
	public my3DGLSLSolver(cs7492Proj3 _p, myRDSolver _rd, base_MarchingCubes _MC, int _cellSize) {
		p = _p; cell3dSize = _cellSize;
		MC = _MC;		
		gridX = p.gridDimX/cell3dSize;
		gridY = p.gridDimY/cell3dSize;
		gridZ = p.gridDimZ/cell3dSize;
		gridPxlW3D = (gridX * gridZ); 			//rectangular to hold pages
		gridPxlH3D =  gridY;	
		maxRad = (gridX/(cell3dSize*10));
		maxDiam = maxRad*2 +1;
		pageSiz = p.gridDimZ;
		gridDistZ = 1.0f/gridZ;//distance in texture between equivalent points in z direction
		RD3Dshader = p.loadShader("reactDiffuse3d.frag");	
		//MCShader = p.loadShader("marchCube3d.frag");	
		
//		shdrBufMC = p.createGraphics(gridPxlW3D, gridPxlH3D, PConstants.P3D);
//		p.outStr2Scr("my3DGLSLSolver " + pCnt++, false);
//		MC.resCubeIdxImg = new PImage(shdrBufMC.width, shdrBufMC.height);
//		p.outStr2Scr("my3DGLSLSolver " + pCnt++, false);
		shdrBuf3D = p.createGraphics(gridPxlW3D, gridPxlH3D, PConstants.P3D); // Image drawn onto by & re-fed to the shader every loop
			
		init3DShader(shdrBuf3D, gridPxlW3D, gridPxlH3D);
	}
	
	public void init3DMC_RD(){
		init3DShader(shdrBuf3D, gridPxlW3D, gridPxlH3D);
	}

	public boolean checkRandAssign(int x, int y, float[] randVal){
		for(int i =0; i<randVal.length; i+=2){if(((x - randVal[i])*(x - randVal[i])) + ((y - randVal[i+1])*(y - randVal[i+1])) < seedSizeSq){return true;}}
		return false;
	}
	public void init3DShader(PGraphics _buf, int w,int h){
        //_buf.beginDraw();
        _buf.loadPixels();
        int idx = 0;
        for(int x = 0; x<w; ++x){
            for(int y = 0; y<h; ++y){ 
               	_buf.pixels[idx++] = 0;//(checkRandAssign(x/cellSize, y/cellSize, randVal)) ? 0x00FF00FF : 0xFF0000FF;
            }      
        }
        _buf.updatePixels();
        //_buf.endDraw();				
	}

	public void setRDSimVals(float _ru, float _rv, float _k, float _f){ru=_ru; rv=_rv; k=_k; f=_f;}

	public void calcConc3dShader(){
		deltaT = p.guiObjs[p.gIDX_deltaT].valAsFloat();
		float diffOnly = p.flags[p.useOnlyDiff] ? 1.0f : 0.0f,
				//dispChemU = p.flags[p.dispChemU] ? 1.0f : 0.0f,
				//isoLevel =  p.flags[p.dispChemU] ?  p.guiObjs[p.gIDX_isoLvl].valAsFloat() : 1.0f - p.guiObjs[p.gIDX_isoLvl].valAsFloat(),
				locMap = p.flags[p.useSpatialParams] ? 1.0f : 0.0f;	
//		RD3Dshader.set("xWidth", gridX);
//		RD3Dshader.set("yHeight", gridY);
	    RD3Dshader.set("ru", ru);
	    RD3Dshader.set("rv", rv);
	    RD3Dshader.set("k", k);
	    RD3Dshader.set("f", f);
	    RD3Dshader.set("distZ", gridDistZ);
	    RD3Dshader.set("deltaT", deltaT);
	    RD3Dshader.set("diffOnly", diffOnly);
	    //RD3Dshader.set("numIters", numShdrIters);
	    RD3Dshader.set("locMap", locMap);		
	    shdrBuf3D.textureMode(PConstants.NORMAL);
		for(int i=0 ; i<numShdrIters ; ++i) {
			// Set the uniforms of the shader			
			RD3Dshader.set("texture",  shdrBuf3D );
			
		    //RD3Dshader.set("dispChemU", dispChemU);		
			// Start drawing into the PGraphics object
			shdrBuf3D.beginDraw();	   
			shdrBuf3D.textureWrap(PConstants.REPEAT);
			shdrBuf3D.shader(RD3Dshader);	
			
			shdrBuf3D.image(shdrBuf3D, 0, 0, gridPxlW3D, gridPxlH3D); 	    
			shdrBuf3D.resetShader();
		    if(p.flags[p.mouseClicked]) {	    	 mouseClick3D();    }
		    	    
			shdrBuf3D.endDraw();
		}	
        //resDataImage.copy(shdrBuf3D, 0, 0, gridPxlW3D, gridPxlH3D, 0, 0,  gridPxlW3D, gridPxlH3D);

        //resDataImage.loadPixels();
	}
		
	//static int t = 0;
	
	public void mouseClick3D(){	
	   shdrBuf3D.pushMatrix();  	shdrBuf3D.noStroke();
	   float d = maxRad, rad=d;
	   //shdrBuf3D.translate(p.mseIn3DBox.x/cell3dSize, p.mseIn3DBox.y/cell3dSize, p.mseIn3DBox.z/cell3dSize);
	   myPoint pt = p.P(p.c.mseIn3DBox);
	   pt._div(cell3dSize);
	   // this click location requires the result to be remapped value by value
//	   float xVal = (float)(pt.y + (gridX * (int)pt.z));
//	   float yVal = (float)(pt.x);
	   float xVal = (float)(pt.x + (gridZ * (int)pt.y));
	   float yVal = (float)(pt.z);
	   
	   //t++;if(t % 10 == 0){t=0;p.outStr2Scr(pt.toStrBrf() + " : to ["+ xVal+", "+ yVal+"]");  	}
	   
	   shdrBuf3D.translate(xVal,yVal);
	   shdrBuf3D.fill(255,255,0,255 );//sets concentration at this location
 	   shdrBuf3D.ellipse( 0,0, rad, rad);

 	   shdrBuf3D.popMatrix();	    
	}
	

	public void drawShaderRes(){		  
       	shdrBuf3D.loadPixels();
       	
//		uncomment to display shader result as flat image
//       	p.pushMatrix();
//       	p.rotate(p.HALF_PI, 1,0,0);
//       	p.rotate(p.HALF_PI, 0,0,1);
//       	p.translate(-.5f*gridPxlW3D, 0);
//       	p.image(shdrBuf3D, 0, 0, gridPxlW3D, gridPxlH3D );  // Display result from shader as image
//       	p.popMatrix();       	
       	
       	MC.copyDataAraToMCLclData(shdrBuf3D.pixels);
	}
	
	public void draw() {		
//		// Display result
		p.pushMatrix();p.pushStyle();//set up as row-col 2x array, not as x-y, so  need to swap width and height to y and x
		//p.translate(-gridPxlW3D*.5f, 0,1);
		drawShaderRes();
		p.popStyle();p.popMatrix();			
		MC.draw(p);
	}
	
}


