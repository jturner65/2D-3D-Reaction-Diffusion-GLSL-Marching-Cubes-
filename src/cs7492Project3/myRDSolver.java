package cs7492Project3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.opengl.PShader;

public class myRDSolver {
	public cs7492Proj3 p;
	// concentration index constants
	public final int chemV = 0,	chemU = 1;

	// structure holding cells in grid
	public myCellGrid cellGrid;	
	public myStencil lapStencil,// laplacian stencil to use for diffusion calculations	
					 custStencil,//custom stencil	
					 currStencil;// the current stencil to use
    public PImage resImageU, resImageV, resDataImage, redMask, greenMask;              // Data buffer
    
    
    public PGraphics shdrBuf2D; 			// Image drawn onto by & re-fed to the shader every loop
    //public PGraphics shdrBuf3D; 			// Image drawn onto by & re-fed to the shader every loop
    //public PGraphics shdrBuf; 				// Image drawn onto by & re-fed to the shader every loop
	
    //2D shader solution
	public PShader RD_shader;

	public int numShdrIters = 20;
//	//use the shader for 3d
	private my3DGLSLSolver glslSolver;
	
	// size of cells - 2d
	public final int cell2dSize = 4;
	//3d 
	public final int cell3dSize = 5;
	// constant rate of diffusion values for u and v chemicals 
	private final float[] ru = new float[] {0.082f, 0.112f, 0.024f};
	private final float[] rv = new float[] {0.041f, 0.061f, 0.012f};
	// default is 0
	private final int diffIDX = 0;

	public final int seedSize = 10, seedNum = 50;			//seed vals to initialze grid - seedNum is 
	
	// multipliers for gray-scott equations
	private float f, k;
	private float deltaT;
	//for 2d grid
	private int gridWidth, gridHeight;						//width in cells
	
	public int gridPxlW2D, gridPxlH2D;
	// index of which concentration to display
	public int dispChem;
	// min/max value found of concentration for each chemical;
	public float[] glMaxConc, glMinConc;

	// arrays to hold results of diffusion calculations for both forward euler
	// and implicit
	public float[][][] Dcnx, // f-euler in x (last col)
						Dcny, // f-euler in y (last col)
						Dcn1x, // implicit in x (last col)
						Dcn1y; // implicit in y (last col)

	public float[][][] concHalfStep; // results of first of 2 steps of ADI
									 // calculation
	//tmp values for ADI calcs
	protected float[] cNi = new float[2];// old concentration value at x,y
	
	// euler calculations in 1D
	protected float[] cNi1x, cNim1x, cNip1x,// cni, cni-1, cni+1 for x calc
			cNi1y, cNim1y, cNip1y;// cni, cni-1, cni+1 for y calc
	//arrays used by tridiag solver
	protected float[] multConstX, multConstY, lower, upper, diag0, diag, diagN, alpha;
	//Precalculate spatial K and F params
	protected float[] spatialK;
	protected float[] spatialF;
	

	public myRDSolver(cs7492Proj3 _p, int gw, int gh) {
		p=_p;
		deltaT = p.guiObjs[p.gIDX_deltaT].valAsFloat();
		// initialize gray-scott constants
		k = 0.0625f;
		f = 0.035f;				
		gridPxlW2D = gw; gridPxlH2D = gh;
		gridWidth = (gw / (cell2dSize));
		gridHeight = (gh / (cell2dSize));
		
		//initialize spatial F and K parameters
		spatialK = new float[gridWidth];
		spatialF = new float[gridHeight];
		for (int i = 0; i < gridWidth; ++i) {spatialK[i] = PApplet.map(i, 0,gridWidth, p.minKSpatial, p.maxKSpatial);}
		for (int j = 0; j < gridHeight; ++j) {spatialF[j] = PApplet.map(j,gridHeight, 0, p.minFSpatial, p.maxFSpatial);}
		
		//2D shader stuff
		shdrBuf2D = p.createGraphics(gridPxlW2D, gridPxlH2D, PConstants.P2D); // Image drawn onto by & re-fed to the shader every loop
		RD_shader = p.loadShader("ReactDiff2D.frag");
		initShaders(true);
		//int pCnt = 1;
		glslSolver = new my3DGLSLSolver(p, this, cell3dSize);
		glslSolver.setRDSimVals(ru[diffIDX], rv[diffIDX], k, f);			
		//2d stuff below		
		init2DCPUStuff();	
	}
	
	public void initShaders(boolean is2D){
		if(is2D){				
			initShader(shdrBuf2D, shdrBuf2D.width, shdrBuf2D.height);		
		} else {
			//3d			
			glslSolver.init3DMC_RD();
		}	
	}
	
	public boolean checkRandAssign(int x, int y, float[] randVal){
		for(int i =0; i<randVal.length; i+=2){if(((x - randVal[i])*(x - randVal[i])) + ((y - randVal[i+1])*(y - randVal[i+1])) < seedSize * seedSize){return true;}}
		return false;
	}
	public void initShader(PGraphics _buf, int w,int h){
		float[] randVal = new float[seedNum];
		for (int i = 0; i < seedNum; ++i) {randVal[i] = p.random(gridWidth);}
		redMask= new PImage(w, h);
		greenMask= new PImage(w, h);
        resImageU = new PImage(w, h);
		resImageV = new PImage(w, h);
        resDataImage = new PImage(w, h);		
        _buf.beginDraw();
        _buf.loadPixels();
        redMask.loadPixels();
        greenMask.loadPixels();
        int idx = 0;
        for(int x = 0; x<gridPxlW2D; ++x){
            for(int y = 0; y<gridPxlH2D; ++y){ 
               	redMask.pixels[idx] = p.color(255,0,0,255);
               	greenMask.pixels[idx] = p.color(0,255,0,255);
               	_buf.pixels[idx++] = (checkRandAssign(x/cell2dSize, y/cell2dSize, randVal)) ? 0x00FF00FF : 0xFF0000FF;
            }      
        }
        redMask.updatePixels();
        greenMask.updatePixels();
        _buf.updatePixels();
        _buf.endDraw();				
	}
	
	public void mseClick2D(){
	    if(p.flags[p.mouseClicked]) {
	    	myPoint msClk = p.c.getMseLoc();
	    	shdrBuf2D.pushStyle();
	    	shdrBuf2D.noStroke();
	    	shdrBuf2D.translate((float)msClk.x, (float)msClk.y);
	    	shdrBuf2D.fill(0,255,0,255 );
	    	shdrBuf2D.rotate(p.drawCount*10 *PConstants.DEG_TO_RAD);
	    	shdrBuf2D.ellipse( 0,0, 5* ((p.drawCount+4)%8), 5* (p.drawCount%24));
	    	shdrBuf2D.fill(255,0,0,255 );
	    	shdrBuf2D.rotate(-p.drawCount*20 *PConstants.DEG_TO_RAD);
	    	shdrBuf2D.ellipse(0, 0, 5* (p.drawCount%8), 5* ((p.drawCount+12)%24));
	    	shdrBuf2D.popStyle();
		    }	
	}// mseClick2D()	
	
	public void calcNewConc2D(boolean useShader2D, boolean useNeumann) {		
		if(useShader2D){calcNewConcShader();}				//shader ignores boundary requests
		else if(useNeumann){calcNewConcNeumann();}
		else{calcNewConcTorroid();}
	}
	
	//perform calculations for shader
	private void calcNewConcShader(){
		//int fillVal = (int)p.random(100)+155;
		deltaT = p.guiObjs[p.gIDX_deltaT].valAsFloat();
		float diffOnly = p.flags[p.useOnlyDiff] ? 1.0f : 0.0f,
				locMap = p.flags[p.useSpatialParams] ? 1.0f : 0.0f;		
	    RD_shader.set("ru", ru[diffIDX]);
	    RD_shader.set("rv", rv[diffIDX]);
	    RD_shader.set("k", k);
	    RD_shader.set("f", f);
	    RD_shader.set("deltaT", deltaT);
	    RD_shader.set("diffOnly", diffOnly);
	    RD_shader.set("locMap", locMap);
		for(int i = 0; i<numShdrIters; ++i){
		    RD_shader.set("texture", shdrBuf2D );
		    		   
		    shdrBuf2D.beginDraw(); 											// begin using the buffer
		    shdrBuf2D.shader(RD_shader);									//assign shader to buffer		    
		    shdrBuf2D.image( shdrBuf2D, 0, 0, shdrBuf2D.width, shdrBuf2D.height ); 		// recycle shader image to shader - this is the data we calculated last time, going back to shader	    
		    shdrBuf2D.resetShader(); 											// Restore default Processing shaders
		    if(p.flags[p.mouseClicked]) {
		    	mseClick2D();
		    }
            shdrBuf2D.endDraw();                  //release graphics object
		}  
        resDataImage.copy( shdrBuf2D, 0, 0, shdrBuf2D.width, shdrBuf2D.height, 0, 0, shdrBuf2D.width, shdrBuf2D.height);
        resDataImage.loadPixels();
	}//calcNewConcShader
	
	public void drawShader2D(){
		PImage res =  drawShaderRes();
		p.image(res,  0, 0, shdrBuf2D.width, shdrBuf2D.height);
	}
	
	public void drawShader3D() {
		glslSolver.draw();
	}

	public void calcShader3D() {
		glslSolver.calcConc3dShader();
	}
	
//	public void drawShader3D(){
//		PImage res =  drawShaderRes();
//		MC.copyDataAraToMCLclData(res.pixels);
//	}
//	
	public PImage drawShaderRes(){		  
	  // Map final image to screen size for display
        if(p.flags[p.dispChemU]){
			resImageU.copy( shdrBuf2D, 0, 0, shdrBuf2D.width, shdrBuf2D.height, 0, 0,  shdrBuf2D.width, shdrBuf2D.height );
			resImageU.blend(greenMask, 0, 0,  shdrBuf2D.width, shdrBuf2D.height ,0, 0,  shdrBuf2D.width, shdrBuf2D.height, PConstants.EXCLUSION );
			resImageU.filter(PConstants.GRAY);
			return resImageU;
		} else {
			resImageV.copy(shdrBuf2D, 0, 0, shdrBuf2D.width, shdrBuf2D.height, 0, 0,  shdrBuf2D.width, shdrBuf2D.height );
			resImageV.blend(redMask, 0, 0,  shdrBuf2D.width, shdrBuf2D.height ,0, 0,  shdrBuf2D.width, shdrBuf2D.height, PConstants.EXCLUSION);
			resImageV.filter(PConstants.GRAY);
			return resImageV;
		}
	}
	
	
	//initialize arrays and stencils used by cpu calculation
	public void init2DCPUStuff(){
		// idx of initial concentration to display
		dispChem = chemU;
		// set up stencil to use for diffusion calculations
		lapStencil = new myStencil(this, 3, 3);
		lapStencil.setStencil(new float[][]{{ 0, 1, 0 },{ 1, -4, 1 },{ 0, 1, 0 } });

		// set up test stencil to test the stencil layout
		custStencil = new myStencil(this, 3, 3);
		custStencil.setStencil(new float[][]{{ .25f, .5f, .25f },{ .5f, -3, .5f },{.25f, .5f, .25f } });
		currStencil = (p.flags[p.useCustomStencil] ? custStencil : lapStencil);// set current stencil to be the laplacian stencil

		initGrid();
		// need to synch these two arrays always
		Dcnx = new float[2][gridHeight][gridWidth];
		Dcny = new float[2][gridWidth][gridHeight];
		Dcn1x = new float[2][gridHeight][gridWidth];
		Dcn1y = new float[2][gridWidth][gridHeight];
		concHalfStep = new float[2][gridWidth][gridHeight];

		//initialize tmp arrays to be used in solvers
		// initialize value for global maximum concentration
		glMaxConc = new float[2];glMinConc = new float[2];		
		
		cNi1x = new float[2];cNim1x = new float[2];cNip1x = new float[2];
		cNi1y = new float[2];cNim1y = new float[2];cNip1y = new float[2];
		multConstX = new float[2];multConstY = new float[2];		
		// for use in implicit solution
		alpha = new float[2];
		lower = new float[2];upper = new float[2];
		diag0 = new float[2];diag = new float[2];diagN = new float[2];		
	}
	
	public void initGrid(){	cellGrid = new myCellGrid(p, this, gridWidth, gridHeight);	}	
	public void setDispChem(int chem){dispChem = chem;}
	
	public void setKF(float[] _kf) { k=_kf[0];f=_kf[1]; 
		glslSolver.setRDSimVals(ru[diffIDX], rv[diffIDX], k, f);
	}	 
	public void setStencil(boolean cust){currStencil = (cust ? custStencil : lapStencil);}
	//tridiag solver for a row -  split out to remove boolean  checks
	private void triDiagRow(float lower, float diag0, float diag, float diagN, float upper, int curX, int curY, int num, int chem,float[] cNp1) {
		float beta;
		float[] gamma = new float[num];
		int lIdx = num-1;

		float b, b0 = (cellGrid.cellMap[0][curY].conc[chem][0]);

		if (diag0 == 0.0) { System.out.println("Cannot have zero in first element of diagonal."); return; } 
		else {
			beta = diag0;
			cNp1[0] = b0 / beta;

			for (int j = 1; j < num -1; j++) {
				gamma[j] = upper / beta;
				beta = (diag) - (lower * gamma[j]);
				b = (cellGrid.cellMap[j][curY].conc[chem][0]);
				cNp1[j] = (b - lower * cNp1[j - 1]) / beta;
			}// for j
			gamma[(num-1)] = upper / beta;
			beta = diagN - (lower * gamma[(num-1)]);
			b = (cellGrid.cellMap[lIdx][curY].conc[chem][0]);
			cNp1[(num-1)] = (b - lower * cNp1[(num-1) - 1]) / beta;

			for (int j = num - 2; j >= 0; j--) { cNp1[j] -= gamma[j + 1] * cNp1[j + 1]; }
		}// if diag not 0
	}// triDiagRow solver
	//tridiag solver for a col - split out to remove boolean  checks
	private void triDiagCol(float lower, float diag0, float diag, float diagN, float upper, int curX, int curY, int num, int chem,float[] cNp1) {
		float beta;
		float[] gamma = new float[num];
		int lIdx = num-1;
		
		float b, b0 = (cellGrid.cellMap[curX][0].conc[chem][0]);

		if (diag0 == 0.0) { System.out.println("Cannot have zero in first element of diagonal."); return; } 
		else {
			beta = diag0;
			cNp1[0] = b0 / beta;
			for (int j = 1; j < num -1; j++) {
				gamma[j] = upper / beta;
				beta = (diag) - (lower * gamma[j]);
				b = (cellGrid.cellMap[curX][j].conc[chem][0]);
				cNp1[j] = (b - lower * cNp1[j - 1]) / beta;
			}// for j
			gamma[(num-1)] = upper / beta;
			beta = diagN - (lower * gamma[(num-1)]);
			b = (cellGrid.cellMap[curX][lIdx].conc[chem][0]);
			cNp1[(num-1)] = (b - lower * cNp1[(num-1) - 1]) / beta;

			for (int j = num - 2; j >= 0; j--) { cNp1[j] -= gamma[j + 1] * cNp1[j + 1]; }
		}// if diag not 0
	}// triDiagCol

	/**
	 * this calculates the appropriate ADI-derived concentration of both chemicals and populates them for each cell at IDX updateIDX 
	 * @param alpha 2-element array holding the alpha values for each chemical
	 * @param updateIDX the index to update in the concentration array for each cell
	 */
	
	public void calcADIDiff(float[] alpha, int updateIDX){
		for (int x = 0; x < cellGrid.gridWidth; ++x) {
			for (int y = 0; y < cellGrid.gridHeight; ++y) {
				for (int chem = 0; chem < 2; ++chem) {
					cNi[chem] = cellGrid.cellMap[x][y].conc[chem][0];//(chem, 0);
					multConstX[chem] = 1;
					multConstY[chem] = 1;
					// calc 1D euler :
					// check neumann boundaries 
					if (x == 0) {									cNim1x[chem] = 0; cNip1x[chem] = cellGrid.cellMap[x+1][y].conc[chem][0];}
					else if (x == cellGrid.gridWidth - 1) {			cNim1x[chem] = cellGrid.cellMap[x-1][y].conc[chem][0]; cNip1x[chem] = 0;}
					else {											cNim1x[chem] = cellGrid.cellMap[x-1][y].conc[chem][0]; 
																	cNip1x[chem] = cellGrid.cellMap[x+1][y].conc[chem][0];
																	multConstX[chem] = 2; }

					cNi1x[chem] = alpha[chem] * (cNim1x[chem] + cNip1x[chem] - (multConstX[chem] * cNi[chem]));

					if (y == 0) {									cNim1y[chem] = 0; cNip1y[chem] = cellGrid.cellMap[x][y+1].conc[chem][0];}
					else if (y == cellGrid.gridHeight - 1) {		cNim1y[chem] = cellGrid.cellMap[x][y-1].conc[chem][0]; cNip1y[chem] = 0;}
					else {											cNim1y[chem] = cellGrid.cellMap[x][y-1].conc[chem][0];
																	cNip1y[chem] = cellGrid.cellMap[x][y+1].conc[chem][0];
																	multConstY[chem] = 2; }					// cNi1y = cNi + ( alpha * (cNim1y + cNip1y -
					cNi1y[chem] = alpha[chem] * (cNim1y[chem] + cNip1y[chem] - (multConstY[chem] * cNi[chem]));
	
					Dcnx[chem][y][x] = cNi1x[chem];
					Dcny[chem][x][y] = cNi1y[chem];
	
					// use forward euler in x + implicit in y (across each row to find first intermediate value then forward euler in y + implicit in x to find final value for diffusion
					// REMEMBER - implicit must have initial concentration value subtracted from result
	
					// first half step
					concHalfStep[chem][x][y] = cNi[chem] + (.5f) * (Dcny[chem][x][y] + (Dcn1x[chem][y][x] - cNi[chem]));
					// second half step
					float concFullStep = concHalfStep[chem][x][y] + (.5f) * (Dcnx[chem][y][x] + (Dcn1y[chem][x][y] - cNi[chem]));
					cellGrid.setConcAtCell(x, y, chem, updateIDX, concFullStep);
	
				}// for both chemicals
			}// for each cell across y - j
		}// for each cell across x - i				
	}//calcADIDiff
	
	/**
	 * this will calculate diffusion solely based on 1d implicit in x and y and using the fact that they are separable to find the 2d soln
	 * @param updateIDX the index to update in the concentration array of each cell
	 */
	
	public void calcPureImplicit(int updateIDX){
		float[] cNi = new float[2];// old concentration value at x,y
		for (int x = 0; x < cellGrid.gridWidth; ++x) {
			for (int y = 0; y < cellGrid.gridHeight; ++y) {
				for (int chem = 0; chem < 2; ++chem) {
					cNi[chem] = cellGrid.cellMap[x][y].conc[chem][0];//getConc(chem, 0);
					cellGrid.setConcAtCell(x, y, chem, updateIDX, (Dcn1x[chem][y][x] + Dcn1y[chem][x][y]) - cNi[chem]);
				}//for each chem
			}//for y = 0 to grid Height
		}//for x = 0 to grid grid2D_X		
	}//calcPureImplicit	
	//set up concentration calculations for imp/ADI calculations
	public void initConcCalc(){
		alpha[chemU] = ru[diffIDX] * deltaT;// /(cell2dSize);
		alpha[chemV] = rv[diffIDX] * deltaT;// /(cell2dSize);
		for (int chem = 0; chem < 2; ++chem) {
			lower[chem] = -alpha[chem];// -alpha
			upper[chem] = -alpha[chem];// -alpha
			diag0[chem] = 1 + alpha[chem]; // 1+alpha
			diag[chem] = 1 + (2 * alpha[chem]);// 1+2alpha
			diagN[chem] = 1 + alpha[chem]; // 1+alpha
			// reinitialize min and max values for each chemical, used to scale
			glMaxConc[chem] = Integer.MIN_VALUE;
			glMinConc[chem] = Integer.MAX_VALUE;		
		}				
	}//initConcCalc
	
	 //this function will calculate the new concentrations of the two chemicals for each cell
	private void calcNewConcTorroid() {
		deltaT = p.guiObjs[p.gIDX_deltaT].valAsFloat();
		if ((!p.flags[p.useADI]) && (!p.flags[p.pureImplicit])) {// calculate diffusion using forward euler - performed in stencil code
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					currStencil.calcValueFETorroid(cellGrid.cellMap[i][j], cellGrid, ru[diffIDX],rv[diffIDX], 1, deltaT);
				}// for each cell across y - j
			}// for each cell across x - i
			if (p.flags[p.useOnlyDiff]){updateCellsOnlyDiff(deltaT, 1);} else 
			{							updateCells(deltaT, 1);	}
		} else {// calculate diffusion using ADI or pure imp
			initConcCalc();
			calcConcImpAdi();
		}// whether to use forward euler or ADI mechanism for diffusion

	}// calcNewConcentration
	
	 //this function will calculate the new concentrations of the two chemicals for each cell
	private void calcNewConcNeumann() {
		deltaT = p.guiObjs[p.gIDX_deltaT].valAsFloat();
		// index of concentrations to use to update to idx 0 upon update step
		int updateIDX = 1;

		if ((!p.flags[p.useADI]) && (!p.flags[p.pureImplicit])) {// calculate diffusion using forward euler - performed in stencil code
			updateIDX = 1;
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					currStencil.calcValueFENeumann(cellGrid.cellMap[i][j], cellGrid, ru[diffIDX],rv[diffIDX], updateIDX, deltaT); 
				}// for each cell across y - j
			}// for each cell across x - i
			if (p.flags[p.useOnlyDiff]){updateCellsOnlyDiff(deltaT, updateIDX);} else 
			{							updateCells(deltaT, updateIDX);	}
		} else {// calculate diffusion using ADI or pure imp
			initConcCalc();
			calcConcImpAdi();
		}// whether to use forward euler or ADI mechanism for diffusion
	}// calcNewConcentration
	
	public void calcConcImpAdi(){
		int updateIDX = 3;
		// for each row y, calculate row's worth of implicit in x for both chems
		for (int y = 0; y < cellGrid.gridHeight; ++y) {
			triDiagRow(lower[0], diag0[0], diag[0], diagN[0], upper[0], -1, y, cellGrid.gridWidth, 0, Dcn1x[0][y]);
			triDiagRow(lower[1], diag0[1], diag[1], diagN[1], upper[1], -1, y, cellGrid.gridWidth, 1, Dcn1x[1][y]);
		}
		// for each column x, calculate col's worth of implicit in y
		for (int x = 0; x < cellGrid.gridWidth; ++x) {
			triDiagCol(lower[0], diag0[0], diag[0], diagN[0], upper[0], x, -1, cellGrid.gridHeight, 0, Dcn1y[0][x]);
			triDiagCol(lower[1], diag0[1], diag[1], diagN[1], upper[1], x, -1, cellGrid.gridHeight, 1, Dcn1y[1][x]);
		}
		if (p.flags[p.pureImplicit]){calcPureImplicit(updateIDX); }				
		else 					{calcADIDiff(alpha, updateIDX);}
		if (p.flags[p.useOnlyDiff]){updateCellsImpOnlyDiff(deltaT, updateIDX);} 
		else {						updateCellsImp(deltaT, updateIDX);	}
	}
	//2 functions to so to not have boolean check every iteration for every cell
	public void updateCellsImpOnlyDiff( float deltaT, int updateIDX){
		//float local_f = f, local_k =k;
		for (int i = 0; i < cellGrid.gridWidth; ++i) {
			for (int j = 0; j < cellGrid.gridHeight; ++j) {
				cellGrid.updateConcAtCellIMP(i, j, updateIDX);
				glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
				glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
				glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
			}// for j
		}// for i		
		
	}
	
	public void updateCellsOnlyDiff(float deltaT, int updateIDX){
		//float local_f = f, local_k =k;
		for (int i = 0; i < cellGrid.gridWidth; ++i) {
			for (int j = 0; j < cellGrid.gridHeight; ++j) {
				cellGrid.updateConcAtCell(i, j, updateIDX);
				glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
				glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
				glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
			}// for j
		}// for i		
	}
		
	//2 functions to so to not have boolean check every iteration for every cell minKSpatial =  0.03f,  maxKSpatial =0.07f, minFSpatial =  0,  maxFSpatial = 0.08f;
	public void updateCellsImp( float deltaT, int updateIDX){
		float local_f, local_k;
		if (p.flags[p.useSpatialParams]) {
			//spatially varying k and f
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				local_k = spatialK[i];
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					local_f = spatialF[i];
					cellGrid.cellMap[i][j].calcReactive(local_f, local_k, deltaT, updateIDX);
					cellGrid.updateConcAtCellIMP(i, j, updateIDX);
					glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
					glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				}// for j
			}// for i	
		} else {
			//constant k and f
			local_k = k;
			local_f = f;
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					cellGrid.cellMap[i][j].calcReactive(local_f, local_k, deltaT, updateIDX);
					cellGrid.updateConcAtCellIMP(i, j, updateIDX);
					glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
					glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				}// for j
			}// for i				
		}	
	}//updateCellsImp
	
	public void updateCells(float deltaT, int updateIDX){
		float local_f, local_k;
		if (p.flags[p.useSpatialParams]) {
			//spatially varying k and f
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				local_k = spatialK[i];
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					local_f = spatialF[i];
					cellGrid.cellMap[i][j].calcReactive(local_f, local_k, deltaT, updateIDX);
					cellGrid.updateConcAtCell(i, j, updateIDX);
					glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
					glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				}// for j
			}// for i			
		} else {
			//constant k and f	
			local_k = k;
			local_f = f;
			for (int i = 0; i < cellGrid.gridWidth; ++i) {
				for (int j = 0; j < cellGrid.gridHeight; ++j) {
					cellGrid.cellMap[i][j].calcReactive(local_f, local_k, deltaT, updateIDX);
					cellGrid.updateConcAtCell(i, j, updateIDX);
					glMaxConc[chemU] = Math.max(glMaxConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMaxConc[chemV] = Math.max(glMaxConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
					glMinConc[chemU] = Math.min(glMinConc[chemU],cellGrid.cellMap[i][j].conc[chemU][0]);
					glMinConc[chemV] = Math.min(glMinConc[chemV],cellGrid.cellMap[i][j].conc[chemV][0]);
				}// for j
			}// for i	
		}
	}//updateCells
	
	public int getIDXFromLoc(float loc){return (int)(loc/cell2dSize);}
	//return a 4 element array with u, v, k and f values at a particular cell location
    public String getUVKFAtCellAsString(){//float mseX, float mseY, float mseZ){
		float local_k = k, local_f = f; String typeStr = "Cell";
		float[] UV;
		int cellX, cellY, cellZ;
		if(p.flags[p.useShader2D]){
			cellX = PApplet.max(0,PApplet.min(shdrBuf2D.width-1, (int)(p.mseCurLoc2D.x))); 
			cellY = PApplet.max(0,PApplet.min(shdrBuf2D.height-1, (int)(p.mseCurLoc2D.y)));
			cellZ = 0;//p.max(0,p.min(cellGrid.gridDepth-1,getIDXFromLoc(mseLoc.z)));      //if we get 3d working, use this
			int idx = cellX + (cellY*shdrBuf2D.width);
			int pixClr = resDataImage.pixels[idx];
			float rClr = pixClr >> 16 & 0xFF, gClr = pixClr >> 8 & 0xFF;
			UV = new float[]{rClr/255.0f, gClr/255.0f};
			if(p.flags[p.useSpatialParams]){
				local_k = ((p.flags[p.useSpatialParams]) ? (PApplet.map(cellX, 0,shdrBuf2D.width, p.minKSpatial, p.maxKSpatial)) : (k));
				local_f = ((p.flags[p.useSpatialParams]) ? (PApplet.map(cellY,shdrBuf2D.height, 0, p.minFSpatial, p.maxFSpatial)) : (f));  
			}            
			typeStr = "Pixel";
		} else {
			cellX = PApplet.max(0,PApplet.min(cellGrid.gridWidth-1, getIDXFromLoc((float)p.mseCurLoc2D.x)));
			cellY = PApplet.max(0,PApplet.min(cellGrid.gridHeight-1, getIDXFromLoc((float)p.mseCurLoc2D.y)));
			cellZ = 0;//p.max(0,p.min(cellGrid.gridDepth-1,getIDXFromLoc(mseLoc.z)));      //if we get 3d working, use this           
			UV = new float[]{cellGrid.cellMap[cellX][cellY].conc[chemU][0], cellGrid.cellMap[cellX][cellY].conc[chemV][0]};
			if(p.flags[p.useSpatialParams]){
				local_k = ((p.flags[p.useSpatialParams]) ? (PApplet.map(cellX, 0,cellGrid.gridWidth, p.minKSpatial, p.maxKSpatial)) : (k));
				local_f = ((p.flags[p.useSpatialParams]) ? (PApplet.map(cellY,cellGrid.gridHeight, 0, p.minFSpatial, p.maxFSpatial)) : (f));  
			}
		}
		String res = typeStr+" ("+ cellX + ","+cellY+") : U = " + String.format("%.5f", UV[0]) + " V = " + String.format("%.5f", UV[1]) + " k = " + String.format("%.5f", local_k) + " f = "  + String.format("%.5f", local_f);  
		return res;
		}
	/**
	 * draws the actual scene by drawing each cell individually, called from
	 * draw()
	 */
	public void drawScene() {
		p.pushMatrix();
		for(int x=0;x<cellGrid.cellMap.length;++x){
			for(int y=0;y<cellGrid.cellMap[x].length;++y){
				cellGrid.cellMap[x][y].draw(Math.round(255 * (cellGrid.cellMap[x][y].conc[dispChem][0] - glMinConc[dispChem])/(glMaxConc[dispChem] - glMinConc[dispChem])));
			}
		}
		p.popMatrix();
	}// drawScene function
	

	public FloatBuffer allocateDirectFloatBuffer(int n) {
		return ByteBuffer.allocateDirect(n * Float.SIZE/8).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}
	
}
