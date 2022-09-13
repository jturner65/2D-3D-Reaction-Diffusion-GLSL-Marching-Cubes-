package cs7492Project3;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import processing.core.*;
/**
 * cs7492 project 3 reaction diffusion
 * 
 * @author john turner
 */
public class cs7492Proj3 extends PApplet {

	public String prjNmLong = "cs7492Proj3", prjNmShrt = "Prj3";
	
	public int solverType;												//solver type used for 2d solver = 0 == fwd euler, 1 == pure imp, 2 == ADI
	public final int slvFE = 0, slvImp = 1, slvADI = 2, slv2DShdr = 3;
	public int currDispType;											//which type of display is selected : spots, stripes, swirls, custom
	public final int dispSpot = 0, dispStripe = 1, dispSwirl = 2, dispCust = 3;
	public final float[] spaceParamsDelT = new float[]{1.0f, 3.0f, 2.4f, 2.0f };
	public final float[] initialDelT = new float[]{1.0f, 3.0f, 2.4f, 2.0f };
	public float[] curKFVals = new float[] {0.0625f,0.035f};
	public final float[][][] kfVals = new float[][][]{					//1st idx is disp type, 2nd idx is solver type, last idx is k and f
			{{0.0625f,0.035f},										//spots : given values for forward euler for spots
				{0.058f,0.024f},									//pure imp
				{0.058f,0.023f},									//adi
				{0.050f,0.016f}},
			{{0.06f,0.035f},										//stripes : given values for forward euler for stripes
				{0.061f,0.045f},
				{0.06125f,0.045f},
				{0.0525f,0.0225f}},
			{{0.0475f,0.0118f},										//swirls : given values for forward euler for spirals
				{0.048f,0.013f},
				{0.048f,0.012f},
				{0.0423f,0.0111f}},
			{{0.05f,0.01f},
				{0.062f,0.052f},
				{0.04f,0.008f},
				 {0.0355f,0.0083f}}};
	public String[][] kfValStr;
	
	public myRDSolver RD;
	public ExecutorService th_exec;
	public int numThreadsAvail;
	public static void main(String[] passedArgs) {
	    String[] appletArgs = new String[] { "cs7492Project3.cs7492Proj3" };
	    if (passedArgs != null) {
	    	PApplet.main(PApplet.concat(appletArgs, passedArgs));
	    } else {
	    	PApplet.main(appletArgs);
	    }
	 }
	public void settings(){
		size((int)(displayWidth*.95f), (int)(displayHeight*.9f),P3D);
	}
	
	public void setup(){
		//size((int)(displayWidth*.95f), (int)(displayHeight*.9f),OPENGL);
		initOnce();
		background(bground[0],bground[1],bground[2],bground[3]);		
	}//setup
	
	public void draw(){			
		cyclModCmp = (drawCount % guiObjs[gIDX_cycModDraw].valAsInt() == 0);
		if((flags[runSim]) && (drawCount%scrMsgTime==0)){if(consoleStrings.size() != 0){consoleStrings.pollFirst();}}
		pushMatrix();pushStyle();
		drawSetup();																//initialize camera, lights and scene orientation and set up eye movement
		if ((!cyclModCmp) || (flags[runSim])) {drawCount++;}			//needed to stop draw update so that pausing sim retains animation positions

		if(flags[show3D]){draw3D_show3D();}
		else {		draw3D_solve2D();	}
		c.buildCanvas();	
		popStyle();popMatrix(); 
		drawUI();	
	}//draw
	public void draw3D_show3D(){
		if(flags[runSim]){	
			RD.glslSolver.calcConc3dShader();						
		}
		translate((float)focusTar.x,(float)focusTar.y,(float)focusTar.z);				//center of screen		
		if (cyclModCmp) {	
			background(bground[0],bground[1],bground[2],bground[3]);	
			pushMatrix();pushStyle();
			translate(-gridDimX/2.0f,-gridDimY/2.0f,-gridDimZ/2.0f);				//center of screen		
			RD.glslSolver.draw();
			popStyle();popMatrix();
			drawAxes(100,3, new myPoint(-viewDimW/2.0f+40,0.0f,0.0f), 200, false); 		//for visualisation purposes and to show movement and location in otherwise empty scene
		}
		drawBoxBnds();
	}
	
	public void drawBoxBnds(){
		pushStyle();
		strokeWeight(3f);
		noFill();
		setColorValStroke(gui_TransGray);
		box(gridDimX,gridDimY,gridDimZ);
		popStyle();		
	}	
	
	public void draw3D_solve2D(){
		if(flags[runSim]){	
			if(flags[useShader2D]){RD.calcNewConcShader();}				//shader ignores boundary requests
			else if(flags[useNeumann]){RD.calcNewConcNeumann();}
			else{RD.calcNewConcTorroid();}
		}
		translate((float)focusTar.x,(float)focusTar.y,(float)focusTar.z);				//center of screen		
		if (cyclModCmp) {
			background(bground[0],bground[1],bground[2],bground[3]);			
			if(flags[useShader2D]){RD.drawShader2D();}
			else {RD.drawScene();}
			drawAxes(100,3, new myPoint(-viewDimW/2.0f+40,0.0f,0.0f), 200, false); 		//for visualisation purposes and to show movement and location in otherwise empty scene
		}
	}//draw3D	
	
	public void drawUI(){
		drawSideBar();					//draw clickable side menu	
		if((!flags[runSim]) || cyclModCmp){
			drawOnScreenData();
		}
		if (flags[saveAnim]) {	savePic();}
	}//drawUI
	
	//called once at start of program
	public void initOnce(){
		initVisOnce();						//always first		
		//thread executor for multithreading
		numThreadsAvail = Runtime.getRuntime().availableProcessors();
		pr("# threads : "+ numThreadsAvail);
		th_exec = Executors.newFixedThreadPool(numThreadsAvail);
		//th_exec = Executors.newCachedThreadPool();
		
		RD = new myRDSolver(this, grid2D_X, grid2D_Y);
		
		kfValStr = new String[kfVals.length*2][kfVals[0].length+1];
		String[] tmp = new String[kfVals[0].length+1];
		for(int i = 0; i< kfVals.length; ++i ){
			tmp = new String[kfVals[0].length+1];
			tmp[0] = dispBtnNames[i]+" K";
			for(int j=0;j<kfVals[i].length; ++j){tmp[j+1] = String.format("%.5f",kfVals[i][j][0]);}
			kfValStr[i*2] = tmp;
			tmp = new String[kfVals[0].length+1];
			tmp[0] = dispBtnNames[i]+" F";
			for(int j=0;j<kfVals[i].length; ++j){tmp[j+1] = String.format("%.5f",kfVals[i][j][1]);}
			kfValStr[(i*2)+1 ] = tmp;
		}
	

		this.setSolverType(0);						//starts with fwd euler : //solver type used for 2d solver = 0 == fwd euler, 1 == pure imp, 2 == ADI
		currDispType=0;						//start with spots
		curKFVals[0] = kfVals[currDispType][solverType][0];
		curKFVals[1] = kfVals[currDispType][solverType][1];
		flags[runSim] = true;				// set sim to run initially
		flags[dispChemU] = true;				// set sim to run initially
		guiObjs[gIDX_deltaT].val = 2.5f;
		guiObjs[gIDX_isoLvl].val = .8;			//initial iso level for U
		focusTar = new myVector(focusVals[(flags[show3D] ? 1 : 0)]);
		initProgram();
	}//initOnce
	
	public void initProgram(){
		initVisProg();				//always first
		resetSolver(true);
		drawCount = 0;
	}//initProgram
	//reset solver values - use this to set k and f values for solver
	public void resetSolver(boolean useDfltKF){
		if(useDfltKF){			//if we use defaults, otherwise allow gui modified values
			RD.initGrid();
			RD.setDispChem(flags[dispChemU] ? 1 : 0);
			curKFVals[0] = kfVals[currDispType][solverType][0];
			curKFVals[1] = kfVals[currDispType][solverType][1];
			this.guiObjs[gIDX_currKVal].setVal(curKFVals[0]);
			this.guiObjs[gIDX_currFVal].setVal(curKFVals[1]);
		}
		RD.setKF(curKFVals);
	}

	//debug data to display on screen
	//get string array for onscreen display of debug info for each object
	public String[] getDebugData(){
		ArrayList<String> res = new ArrayList<String>();
		List<String>tmp;
		for(int j = 0; j<numGuiObjs; j++){tmp = Arrays.asList(guiObjs[j].getStrData());res.addAll(tmp);}
		return res.toArray(new String[0]);	
	}
	
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	public void setFlags(int idx, boolean val ){
		flags[idx] = val;
		switch (idx){
			case debugMode 			: {  break;}//anything special for debugMode 			
			case saveAnim 			: {  break;}//anything special for saveAnim 			
			case shiftKeyPressed 	: {  break;}//anything special for shiftKeyPressed 	
			case mouseClicked 		: {  break;}//anything special for mouseClicked 		
			case modView	 		: {  break;}//anything special for modView	 		
			case runSim				: {  break;}//anything special for runSim				
			case useOnlyDiff 		: {  break;}//anything special for useOnlyDiff 		
			case useSpatialParams  	: {  
				if(val){				guiObjs[gIDX_deltaT].setVal(spaceParamsDelT[solverType]);}//set timestep appropriate for spatial params depending on solver
				else {					guiObjs[gIDX_deltaT].setVal(initialDelT[solverType]);}
				initProgram();	
				break;}									//anything special for useSpatialParams  	
			case useCustomStencil  	: {  RD.setStencil(flags[useCustomStencil]);break;}			//anything special for useCustomStencil  	
			case useADI 			: {  
				if (val) {
					setSolverType(slvADI);
				} else {setSolverType(slvFE);}				
				initProgram();	
				break;}		//anything special for useADI 			
			case pureImplicit		: {  
				if (val) {
					setSolverType(slvImp);
				} else {setSolverType(slvFE);}				
				initProgram();	
				break;}				//anything special for pureImplicit	
			case useNeumann			: {  break;}//anything special for useNeumann			
			case dispChemU			: { RD.setDispChem((val ? 1 : 0)); break;}
			case show3D			: { 
				setCamView(); 
				setSolverType(slv2DShdr);
				if(val){
					flags[useShader2D]=false;initProgram();
					RD.glslSolver.init3DMC_RD();
				} else {	
					initProgram();
				}			
				break;}
			case useShader2D		: {
				if (val) {
					setSolverType(slv2DShdr);
				} else {setSolverType(slvFE);}
				initProgram();	
				break;}		//anything special for shader 2d 
			case useVertNorms		: {//use vertex normals for shading
				break;}
		}
	}//setFlags  
			
	public void setSolverType(int i){
		solverType = i;
		if(flags[useSpatialParams]){guiObjs[gIDX_deltaT].setVal(spaceParamsDelT[solverType]);}			//set values appropriate for spatial params, if being used
		else {					guiObjs[gIDX_deltaT].setVal(initialDelT[solverType]);}
		switch (solverType){//solver type used for 2d solver = 0 == fwd euler, 1 == pure imp, 2 == ADI
			case slvFE : {setSolverFlags(-1);break;}//{flags[useADI]=false;flags[pureImplicit]=false;flags[useShader2D]=false;break;}
			case slvImp : {setSolverFlags(pureImplicit);break;}//{flags[pureImplicit]=true;flags[useADI]=false;flags[useShader2D]=false;break;}
			case slvADI : {setSolverFlags(useADI);break;}//{flags[useADI]=true;flags[pureImplicit]=false;flags[useShader2D]=false;break;}
			case slv2DShdr : {setSolverFlags(useShader2D);RD.initShader(RD.shdrBuf2D, RD.shdrBuf2D.width, RD.shdrBuf2D.height);break;}//{break;}
		}	
	}
	
	public void setSolverFlags(int idx){//sets passed idx to true, all other solvers false
		flags[useADI]=false;flags[pureImplicit]=false;flags[useShader2D]=false;
		if(idx!=-1){flags[idx] = true;}	
	}
	
	public String dispSolverType(){
		switch (solverType){//solver type used for 2d solver = 0 == fwd euler, 1 == pure imp, 2 == ADI slvFE = 0, slvImp = 1, slvADI = 2
		case slvFE : {return "Forward Euler";}
		case slvImp : {return "Pure Implicit";}
		case slvADI : {return "ADI";}
		case slv2DShdr : {return "Shader FE";}
		}
		return "None";
	}
	public String dispPatternType(){
		switch (currDispType){//pattern type being displayed :  dispSpot = 0, dispStripe = 1, dispSwirl = 2, dispCust = 3;
		case dispSpot : {return "Spots";}
		case dispStripe : {return "Stripes";}
		case dispSwirl : {return "Swirls";}
		case dispCust : {return "Custom";}
		}
		return "None";
	}	
	public void setFocus(int tar){
		focusTar.set(focusVals[(tar+focusVals.length)%focusVals.length]);
		switch (tar){//special handling for each view
		case 0 : {initProgram();break;} //refocus camera on center
		case 1 : {initProgram();break;}  
		case 2 : {break;}         
		case 3 : {break;}        
		case 4 : {break;} //refocus camera on histogram of lrf data		
		}
	}
	
	public void setCamView(){
		int idx = flags[show3D] ? 1 : 0;
		rx = (float)cameraInitLocs[idx].x;
		ry = (float)cameraInitLocs[idx].y;
		dz = (float)cameraInitLocs[idx].z;
		setFocus(idx);
	}
		//find u,v, k, f values at mouse location in sim plane
	public String getUVKF(myPoint p){
		mseCurLoc2D = new myPoint(0,0,0);	//actual click location on visible plane
		intersectPl(p, c.eyeToMse, new myPoint(0,0,0),new myPoint(0,grid2D_Y,0),new myPoint(grid2D_X,grid2D_Y,0),  mseCurLoc2D);//find point where mouse ray intersects canvas
		if(flags[show3D]){mseCurLoc2D = bndClkInBox3D(mseCurLoc2D);} else {mseCurLoc2D = bndClkInBox2D(mseCurLoc2D);}
		//String res = RD.getUVKFAtCellAsString(clickLoc);
		return RD.getUVKFAtCellAsString();	
	}//getUVKF	

	public myPoint bndClkInBox2D(myPoint p){p.set(Math.max(0,Math.min(p.x,grid2D_X)),Math.max(0,Math.min(p.y,grid2D_Y)),0);return p;}
	public myPoint bndClkInBox3D(myPoint p){p.set(Math.max(0,Math.min(p.x,gridDimX)),Math.max(0,Math.min(p.y,gridDimY)),Math.max(0,Math.min(p.z,gridDimZ)));return p;}	
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	
	public void keyPressed(){
		switch (key){
			case '1' : {currDispType = 0;initProgram();break;}
			case '2' : {currDispType = 1;initProgram();break;}
			case '3' : {currDispType = 2;initProgram();break;}
			case '4' : {currDispType = 3;initProgram();break;}
			//case '5' : {setFlags(show3D,!flags[show3D]);break;}							//set to 3d version
			case '6' : {setFocus(1);break;}
			case '7' : {break;}
			case '8' : {break;}
			case '9' : {break;}
			case '0' : {break;}
			case ' ' : {setFlags(runSim,!flags[runSim]); break;}							//run sim
			case 'a' :
			case 'A' : {setFlags(saveAnim,!flags[saveAnim]);break;}						//start/stop saving every frame for making into animation
			case 'c' : 
			case 'C' : {setFlags(useCustomStencil,!flags[useCustomStencil]);break;}
			case 'd' :
			case 'D' : {setFlags(useOnlyDiff,!flags[useOnlyDiff]);break;}				//toggle between diffusion alone and reaction/diffusion			
			case 'e' :
			case 'E' : {setFlags(useADI,!flags[useADI]);break;}							//use ADI - alt dir imp - turn off pure implicit			
			case 'f' :
			case 'F' : {setFlags(pureImplicit,!flags[pureImplicit]);break;}				//use pure implicit - turn off adi
			case 'i' : 
			case 'I' : {initProgram();break;}		//re-start program
			case 'p' :
			case 'P' : {setFlags(useSpatialParams,!flags[useSpatialParams]);break;}//toggle between constant parameters for each cell and location-specific parameters for f and k		
			case 'r' : 
			case 'R' : {break;}
			case 's' :
			case 'S' : {save(sketchPath("") + "\\" + ((flags[useCustomStencil]) ? ("custStencImgs") : ("lapStencImgs") )  + "\\" + "img" + (int)random(10) + "" + (int)random(10) + "" + (int) random(10) + "" + (int)random(10) + ((flags[useCustomStencil]) ? ("custStenc") : ("lapStenc") ) + ".jpg");break;}//save picture of current image			
			case 't' :
			case 'T' : {setFlags(useNeumann,!flags[useNeumann]);break;}//toggle between using torroidal or neuman boundaries			
			case 'u' :
			case 'U' : {setFlags(dispChemU, true);break;}//for each time step, display concentrations of chemical U == 1
			case 'v' :
			case 'V' : {setFlags(dispChemU, false);break;}//for each time step, display concentrations of chemical V	== 0
			case ',' :
			case '<' : {guiObjs[gIDX_deltaT].modVal(-.1f);	break;}//decrease deltaT value to some lower bound			
			case '.' :
			case '>' : {guiObjs[gIDX_deltaT].modVal(.1f);	break;}	
			case ';' :
			case ':' : {guiObjs[gIDX_cycModDraw].modVal(-1); break;}//decrease the number of cycles between each draw, to some lower bound
			case '\'' :
			case '"' : {guiObjs[gIDX_cycModDraw].modVal(1); break;}//increase the number of cycles between each draw to some upper bound		
			default : {	}
		}//switch	
		
		if((!flags[shiftKeyPressed])&&(key==CODED)){flags[shiftKeyPressed] = (keyCode  == KeyEvent.VK_SHIFT);}
	}
	public void keyReleased(){
		if((flags[shiftKeyPressed])&&(key==CODED)){ if(keyCode == KeyEvent.VK_SHIFT){clearFlags(new int []{shiftKeyPressed, modView});}}
	}

	//2d range checking of point
	public boolean ptInRange(float x, float y, float minX, float minY, float maxX, float maxY){return ((x > minX)&&(x < maxX)&&(y > minY)&&(y < maxY));}	
	/**
	 * handle mouse presses - print out to console value of particular cell
	 */
	public void mousePressed() {
		if(mouseX<(menuWidth)){//check where mouse is - if in range of side menu, process clicks for ui input	
			if(mouseX>(menuWidth-15)&&(mouseY<15)){showInfo =!showInfo; return;}			//turn on/off info header - click in mouse box
			if(mouseY<20){return;}
			int i = (int)((mouseY-(yOff))/(yOff));
			if(clkyFlgs.contains(i)){setFlags(i,!flags[i]);}
			else if(ptInRange(mouseX, mouseY, minClkX, minSlvClkY,	maxClkX, minDispTypClkY)){//in region where clickable buttons are for solver select 
				setSolverType((int)(slvBtnNames.length * (mouseX)/menuWidth)   );initProgram();return;}
			else if(ptInRange(mouseX, mouseY, minClkX, minDispTypClkY,	maxClkX, minGuiClkY)){//in region where clickable buttons are for disp type select 
				currDispType = (int)(dispBtnNames.length * (mouseX)/menuWidth) ;initProgram();return;}
			else if(ptInRange(mouseX, mouseY, minClkX, minGuiClkY,	maxClkX, maxGuiClkY)){//in clickable region for UI interaction
				for(int j=0; j<numGuiObjs; ++j){
					if(guiObjs[j].clickIn(mouseX, mouseY)){	msClkObj=j;return;	}
				}
				if((msClkObj != -1) && (guiObjs[msClkObj].kfIdx != -1)){guiObjs[msClkObj].updateKF();}
			}//1st flock check if click in range of modifiable fields for 1st flock
		}//handle menu interaction
		else {
			if(!flags[shiftKeyPressed]){flags[mouseClicked] = true;}
		}	
	}// mousepressed	

	
	public void mouseDragged(){
		if(msClkObj!=-1){	guiObjs[msClkObj].modVal((mouseX-pmouseX)+(mouseY-pmouseY)*-5.0f);}//if not -1 then already modifying value, no need to pass or check values of box
		if(mouseX<(width * menuWidthMult)){	//handle menu interaction
		}
		else {
			if(flags[shiftKeyPressed]){
				flags[modView]=true;
				if(mouseButton == LEFT){			rx-=PI*(mouseY-pmouseY)/height; ry+=PI*(mouseX-pmouseX)/width;} 
				else if (mouseButton == RIGHT) {	dz-=(double)(mouseY-pmouseY);}
			}
		}
	}//mouseDragged()
	
	public void mouseReleased(){
		clearFlags(new int[]{mouseClicked, modView});
		if((msClkObj != -1) && (guiObjs[msClkObj].kfIdx != -1)){guiObjs[msClkObj].updateKF();resetSolver(false);	}
		msClkObj = -1;
	}
	
	public void clearFlags(int[] idxs){		for(int idx : idxs){flags[idx]=false;}	}	

	//////////////////////////////////////////
	/// graphics and base functionality utilities and variables
	//////////////////////////////////////////
	//static variables	
	public static int GUIObjID = 0;										//counter variable for gui objs
	
	public final int grid2D_X=800, grid2D_Y=800;	
	public final int gridDimX = 500, gridDimY = 500, gridDimZ = 500;				//dimensions of 3d region
	
	public final int gridDimX2 = gridDimX/2, gridDimY2 = gridDimY/2, gridDimZ2 = gridDimZ/2;				//dimensions of 3d region
	//public final int gridDimX = 500, gridDimY = 500, gridDimZ = 500;				//dimensions of 3d region
	public final float minKSpatial =  0.03f,  maxKSpatial =0.07f, minFSpatial =  0,  maxFSpatial = 0.08f;
	public myVector[] focusVals = new myVector[]{						//set these values to be different targets of focus
			new myVector(-grid2D_X/2,-grid2D_Y/1.75f,0),
			new myVector(0,0,gridDimZ/3.0f)
	};
 

	//visualization variables
	// boolean flags used to control various elements of the program 
	public boolean[] flags;
	
	//dev/debug flags
	public final int debugMode 			= 0;			//whether we are in debug mode or not	
	public final int saveAnim 			= 1;			//whether we are in debug mode or not	
	//interface flags	
	public final int shiftKeyPressed 	= 2;			//shift pressed
	public final int mouseClicked 		= 3;			//mouse left button is held down	
	public final int modView	 		= 4;			//shift+mouse click+mouse move being used to modify the view		
	public final int runSim				= 5;			//run simulation (if off localization progresses on single pose
	
	public final int useOnlyDiff 		= 6;			// whether only diffusion
	public final int useSpatialParams 	= 7;			// whether to use spatial location to determine k and f
	public final int useCustomStencil 	= 8;			// whether to use custom stencil
	public final int useADI 			= 9;			// whether to use ADI methods for integration or not	
	public final int useNeumann			= 10;			// whether to use torroidal or neumann boundaries	
	public final int pureImplicit 		= 11;			// whether this simulation should use purely implicit diffusion	
	public final int dispChemU			= 12;			//display concentrations for chemical U
	public final int useShader2D		= 13;			//use the shader-based 2D fwd euler solver
	public final int show3D				= 14; 			
	public final int useVertNorms		= 15;			//use vertex normals for shading marching cubes

	public final int numFlags = 16;
	
	public boolean showInfo;										//whether or not to show start up instructions for code
	
	public myVector focusTar;										//target of focus - used in translate to set where the camera is looking - set array of vector values (focusVals) based on application
	private boolean cyclModCmp;									//comparison every draw of cycleModDraw	
	
	public final int[] bground = new int[]{240,240,240,255};		//bground color
	
	public final String[] trueFlagNames = {
			"Debug Mode",		
			"Save Anim", 		
			"Shift-Key Pressed",
			"Click interact", 	
			"Changing View",	
			"Execute Simulation",
			"Use Only Diffusion",
			"Use Spatial Params", 
			"Use Custom Stencil", 
			"Use ADI",
			"Use Neumann Bounds", 
			"Use Pure Implicit", 
			"Display U chem conc",
			"Use 2D Shader Fwd Eul",
			"Change back to 2D",//"3D solver disabled"
			"Use MC Vertex Normals"
			};
	
	public final String[] falseFlagNames = {
			"Debug Mode",		
			"Save Anim", 		
			"Shift-Key Pressed",
			"Click interact", 	
			"Changing View",	 	
			"Execute Simulation",
			"Use Only Diffusion",
			"Use Spatial Params", 
			"Use Custom Stencil", 
			"Use ADI",
			"Use Torroidal Bounds", 
			"Use Pure Implicit", 
			"Display V chem conc",
			"Use 2D Shader Fwd Eul",
			"Change to 3D Marching Cubes",
			"Use MC Face Normals"
			};
	
	public int[][] flagColors;
	//List<String> places = Arrays.asList
	//flags that can be modified by clicking on screen
	public List<Integer> clkyFlgs = Arrays.asList(
			debugMode, saveAnim,runSim,useOnlyDiff,useSpatialParams,useCustomStencil,useADI,useNeumann,pureImplicit,dispChemU,useShader2D,show3D,useVertNorms
			);			
	float xOff = 20 , yOff = 20;			//offset values to render boolean menu on side of screen	
	public final float minClkX = 17;
	public float minGuiClkY,minSlvClkY, minDispTypClkY, maxGuiClkY, maxClkY, maxClkX;	
	public int msClkObj;
	
	public myPoint mseCurLoc2D;
	
	//timestep
	//public double deltaT;
	public final double maxDelT = 7;			//max value that delta t can be set to
	//how many frames to wait to actually refresh/draw
	//public int cycleModDraw = 1;
	public final int maxCycModDraw = 20;	//max val for cyc mod draw

	public final int numGuiObjs = 5;
	public myGUIObj[] guiObjs;	
	public final double[][] guiMinMaxModVals = new double [][]{//min max values curKFVals
			{0, maxDelT, .05},													//delta t
			{1, maxCycModDraw, .1},
			{.03, .07, .0001},									//k
			{0, .08, .0001},									//f
			{0, 1, .001}															//Iso level of mc dislay			
	};

	public final String[] guiObjNames = new String[]{"Delta T","Draw Cycle Length","Current K Val","Current F Val", "MC Iso Level"};		
	public final boolean[] guiTrtAsInt = new boolean[]{false,true,false,false, false};
	
	//idx's of objects in gui objs array	
	public final int gIDX_deltaT = 0,
					gIDX_cycModDraw = 1,
					gIDX_currKVal = 2,
					gIDX_currFVal = 3,
					gIDX_isoLvl = 4;
	
	
	//solver and disp type button names
	public final String[] slvBtnNames = new String[]{"Fwd Euler", "Implicit", "ADI", "Shader"};
	public final String[] dispBtnNames = new String[]{"Spots", "Stripes", "Swirls", "Custom"};	
	public final String[] dispStyleNames = new String[]{"Style","Spots", "Stripes", "Swirls", "Custom"};	
	public final String[] slvGridNames = new String[]{"Solver","F Eul", "Imp", "ADI", "Shader"};

		
	// path and filename to save pictures for animation
	public String animPath, animFileName;
	public int animCounter;	
	public final int scrMsgTime = 50;									//5 seconds to delay a message 60 fps (used against draw count)
	public ArrayDeque<String> consoleStrings;							//data being printed to console - show on screen

	public final float camInitialDist = -100,		//initial distance camera is from scene - needs to be negative
				camInitRy = 0,
				camInitRx = -PI/2.0f;
	
	public myVector[] cameraInitLocs = new myVector[]{						//set these values to be different initial camera locations based on 2d or 3d
			new myVector(camInitRx,camInitRy,camInitialDist),
			new myVector(-0.47f,-0.61f,-gridDimZ*.5f)			
		};
	
	public int viewDimW, viewDimH;
	public int drawCount;												// counter for draw cycles
	public int simCycles;
	
	public float menuWidthMult = .15f;									//side menu is 15% of screen grid2D_X
	public float menuWidth;

	public ArrayList<String> DebugInfoAra;								//enable drawing dbug info onto screen
	public String debugInfoString;
	
	//animation control variables	
	public float animCntr, animModMult;
	public final float maxAnimCntr = PI*1000.0f, baseAnimSpd = 1.0f;
	
	private float dz=0, 												// distance to camera. Manipulated with wheel or when
	rx=-0.06f*TWO_PI, ry=-0.04f*TWO_PI;									// view angles manipulated when space pressed but not mouse	

	my3DCanvas c;												//3d stuff and mouse tracking
	public float[] camVals;
	
	public String dateStr, timeStr;								//used to build directory and file names for screencaps
	
	public double msClkEps = 40;									//distance within which to check if clicked from a point
	
	public int[] rgbClrs = new int[]{gui_Red,gui_Green,gui_Blue};
	
	public myVector[] boxNorms = new myVector[] {//normals to 3 d bounding boxes
			new myVector(1,0,0),
			new myVector(-1,0,0),
			new myVector(0,1,0),
			new myVector(0,-1,0),
			new myVector(0,0,1),
			new myVector(0,0,-1),				
	};
	private final float hGDimX = gridDimX/2.0f, hGDimY = gridDimY/2.0f, hGDimZ = gridDimZ/2.0f;
	private final float tGDimX = gridDimX*10, tGDimY = gridDimY*10, tGDimZ = gridDimZ*20;
	public myPoint[][] boxWallPts = new myPoint[][] {//pts to check if intersection with 3D bounding box happens
			new myPoint[] {new myPoint(hGDimX,tGDimY,tGDimZ), new myPoint(hGDimX,-tGDimY,tGDimZ), new myPoint(hGDimX,tGDimY,-tGDimZ)  },
			new myPoint[] {new myPoint(-hGDimX,tGDimY,tGDimZ), new myPoint(-hGDimX,-tGDimY,tGDimZ), new myPoint(-hGDimX,tGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,hGDimY,tGDimZ), new myPoint(-tGDimX,hGDimY,tGDimZ), new myPoint(tGDimX,hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,-hGDimY,tGDimZ),new myPoint(-tGDimX,-hGDimY,tGDimZ),new myPoint(tGDimX,-hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,tGDimY,hGDimZ), new myPoint(-tGDimX,tGDimY,hGDimZ), new myPoint(tGDimX,-tGDimY,hGDimZ)  },
			new myPoint[] {new myPoint(tGDimX,tGDimY,-hGDimZ),new myPoint(-tGDimX,tGDimY,-hGDimZ),new myPoint(tGDimX,-tGDimY,-hGDimZ)  }
	};
	
	///////////////////////////////////
	/// generic graphics functions and classes
	///////////////////////////////////
		//1 time initialization of things that won't change
	public void initVisOnce(){		
		dateStr = "_"+day() + "-"+ month()+ "-"+year();
		timeStr = "_"+hour()+"-"+minute()+"-"+second();
		colorMode(RGB, 255, 255, 255, 255);
		mseCurLoc2D = new myPoint(0,0,0);	
		frameRate(120);
		sphereDetail(4);
		initBoolFlags();
		camVals = new float[]{width/2.0f, height/2.0f, (height/2.0f) / tan(PI/6.0f), width/2.0f, height/2.0f, 0, 0, 1, 0};
		showInfo = true;
		println("sketchPath " + sketchPath(""));
		textureMode(NORMAL);	
		menuWidth = width * menuWidthMult;						//grid2D_X of menu region	
		setupMenuClkRegions();		
		rectMode(CORNER);	
		
		viewDimW = width;viewDimH = height;
		initCamView();	
		simCycles = 0;
		animPath = sketchPath("") + "\\"+prjNmLong+"_" + (int) random(1000);
		animFileName = "\\" + prjNmLong;
		consoleStrings = new ArrayDeque<String>();				//data being printed to console		
		c = new my3DCanvas(this);
	}
	//initialize structure to hold modifiable menu regions
	public void setupMenuClkRegions(){
		minSlvClkY = (numFlags+3) * yOff;
		minDispTypClkY = (numFlags+5) * yOff;
		minGuiClkY = (numFlags+8) * yOff;
		float stClkY = minGuiClkY;
		maxClkX = .99f * menuWidth;
		msClkObj = -1;									//this is the object currently being modified by mouse dragging
		guiObjs = new myGUIObj[numGuiObjs];			//list of modifiable gui objects
		myGUIObj tmp; 
		double stVal;
		for(int i =0; i< numGuiObjs; ++i){
			if((i==gIDX_currKVal) || (i==gIDX_currFVal)) {stVal = this.curKFVals[i-2];} else {stVal =guiMinMaxModVals[i][0];}
			tmp = new myGUIObj(this, guiObjNames[i], minClkX, stClkY, maxClkX, stClkY+yOff, guiMinMaxModVals[i][0],guiMinMaxModVals[i][1],stVal, guiTrtAsInt[i], guiMinMaxModVals[i][2]);
			if((i==gIDX_currKVal) || (i==gIDX_currFVal)) {tmp.kfIdx = i-2;}
			stClkY += yOff;
			guiObjs[i] = tmp;
		}
		maxGuiClkY = stClkY;
	}
		//init boolean state machine flags for program
	public void initBoolFlags(){
		flags = new boolean[numFlags];
		flagColors = new int[numFlags][3];
		for (int i = 0; i < numFlags; ++i) { flags[i] = false; flagColors[i] = new int[]{(int) random(150),(int) random(100),(int) random(150)}; }	
	}
		//called every time re-initialized
	public void initVisProg(){	drawCount = 0;		debugInfoString = "";		reInitInfoStr();}	
	public void initCamView(){
		//camEdge = new edge();	
		dz=camInitialDist; 		
		ry=camInitRy;
		rx=camInitRx - ry;	}
	public void reInitInfoStr(){		DebugInfoAra = new ArrayList<String>();		DebugInfoAra.add("");	}	
	public int addInfoStr(String str){return addInfoStr(DebugInfoAra.size(), str);}
	public int addInfoStr(int idx, String str){	
		int lstIdx = DebugInfoAra.size();
		if(idx >= lstIdx){		for(int i = lstIdx; i <= idx; ++i){	DebugInfoAra.add(i,"");	}}
		setInfoStr(idx,str);	return idx;
	}
	public void setInfoStr(int idx, String str){DebugInfoAra.set(idx,str);	}
	public void drawInfoStr(float sc){//draw text on main part of screen
		pushMatrix();		pushStyle();
		fill(0,0,0,100);
		translate((menuWidth),0);
		scale(sc,sc);
		for(int i = 0; i < DebugInfoAra.size(); ++i){		text((flags[debugMode]?(i<10?"0":"")+i+":     " : "") +"     "+DebugInfoAra.get(i)+"\n\n",0,(10+(12*i)));	}
		popStyle();	popMatrix();
	}		
	//vector and point functions to be compatible with earlier code from jarek's class or previous projects	
	
	//drawsInitial setup for each draw
	public void drawSetup(){
		camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);       // sets a standard perspective
		translate((float)width/2.0f,(float)height/2.0f,(float)dz); // puts origin of model at screen center and moves forward/away by dz
	    setCamOrient();
            //noLights();
	    //shininess(.1f);
	    ambientLight(55, 55, 55);
	    lightSpecular(111, 111, 111);
	    directionalLight(255, 255, 255, -1,1,-1);
		//specular(111, 111, 111);
	}//drawSetup	
	public void setCamOrient(){rotateX((float)rx);rotateY((float)ry); rotateX((float)PI/(2.0f));		}//sets the rx, ry, pi/2 orientation of the camera eye	
	public void unSetCamOrient(){rotateX((float)-PI/(2.0f)); rotateY((float)-ry);   rotateX((float)-rx); }//reverses the rx,ry,pi/2 orientation of the camera eye - paints on screen and is unaffected by camera movement
	public void drawAxes(double len, float stW, myPoint ctr, int alpha, boolean centered){//axes using current global orientation
		pushMatrix();pushStyle();
			strokeWeight(stW);
			stroke(255,0,0,alpha);
			if(centered){line(ctr.x-len*.5f,ctr.y,ctr.z,ctr.x+len*.5f,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y-len*.5f,ctr.z,ctr.x,ctr.y+len*.5f,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z-len*.5f,ctr.x,ctr.y,ctr.z+len*.5f);} 
			else {		line(ctr.x,ctr.y,ctr.z,ctr.x+len,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y+len,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y,ctr.z+len);}
		popStyle();	popMatrix();	
	}//	drawAxes
//	public void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int alpha, boolean drawVerts){//RGB -> XYZ axes
//		pushMatrix();pushStyle();
//		if(drawVerts){
//			show(ctr,3,gui_Black);
//			for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),3,rgbClrs[i]);}
//		}
//		strokeWeight(stW);
//		for(int i =0; i<3;++i){	setColorValStroke(rgbClrs[i]);	showVec(ctr,len, _axis[i]);	}
//		popStyle();	popMatrix();	
//	}//	drawAxes
//	public void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int[] clr, boolean drawVerts){//all axes same color
//		pushMatrix();pushStyle();
//			if(drawVerts){
//				show(ctr,2,gui_Black);
//				for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),2,rgbClrs[i]);}
//			}
//			strokeWeight(stW);stroke(clr[0],clr[1],clr[2],clr[3]);
//			for(int i =0; i<3;++i){	showVec(ctr,len, _axis[i]);	}
//		popStyle();	popMatrix();	
//	}//	drawAxes

	public void drawText(String str, double x, double y, double z, int clr){
		int[] c = getClr(clr);
		pushMatrix();	pushStyle();
			fill(c[0],c[1],c[2],c[3]);
			unSetCamOrient();
			translate((float)x,(float)y,(float)z);
			text(str,0,0,0);		
		popStyle();	popMatrix();	
	}//drawText	
	public void savePic(){		save(animPath + animFileName + ((animCounter < 10) ? "000" : ((animCounter < 100) ? "00" : ((animCounter < 1000) ? "0" : ""))) + animCounter + ".jpg");		animCounter++;		}
	public void line(double x1, double y1, double z1, double x2, double y2, double z2){line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2 );}
	public void line(myPoint p1, myPoint p2){line((float)p1.x,(float)p1.y,(float)p1.z,(float)p2.x,(float)p2.y,(float)p2.z);}
	public void drawOnScreenData(){
		if(flags[debugMode]){
			pushMatrix();pushStyle();			
			reInitInfoStr();
			addInfoStr(0,"mse loc on screen : " + new myPoint(mouseX, mouseY,0) + " mse loc in world :"+c.mseLoc +"  Eye loc in world :"+ c.eyeInWorld); 
			String[] res = getDebugData();
			//for(int s=0;s<res.length;++s) {	addInfoStr(res[s]);}				//add info to string to be displayed for debug
			int numToPrint = min(res.length,80);
			for(int s=0;s<numToPrint;++s) {	addInfoStr(res[s]);}				//add info to string to be displayed for debug
			drawInfoStr(1.0f); 	
			popStyle();	popMatrix();		
		}
		else if(showInfo){
			pushMatrix();pushStyle();			
			reInitInfoStr();	
			if(showInfo){
		      addInfoStr(0,"Click the light green box to the left to toggle showing this message.");
		      addInfoStr(1,"--Shift-Click-Drag to change view.  Shift-RClick-Drag to zoom.");
             // addInfoStr(3,"Values at Mouse Location : "+ values at mouse location);
			}
			String[] res = consoleStrings.toArray(new String[0]);
			int dispNum = min(res.length, 80);
			for(int i=0;i<dispNum;++i){addInfoStr(res[i]);}
		    drawInfoStr(1.1f); 
			popStyle();	popMatrix();	
		}
	}
	//print informational string data to console, and to screen

	public void outStr2Scr(String str){outStr2Scr(str,true);}
		//print informational string data to console, and to screen
	public void outStr2Scr(String str, boolean showDraw){
		System.out.println(str);
		String[] res = str.split("\\r?\\n");
		if(showDraw){
			for(int i =0; i<res.length; ++i){
				consoleStrings.add(res[i]);		//add console string output to screen display- decays over time
			}
		}
	}

	public void drawButtons(String[] btnNames, int cmpVal){
		float xWidthOffset = menuWidth/(1.0f * btnNames.length), halfWay;
		pushMatrix();pushStyle();
		strokeWeight(.5f);
		stroke(0,0,0,255);
		noFill();
		translate(-xOff*.5f, 0, 0);
		for(int i =0; i<btnNames.length;++i){
			halfWay = (xWidthOffset - textWidth(btnNames[i]))/2.0f;
			if(i==cmpVal){fill(0,255,0,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);} 
			else {			fill(200,200,200,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);}
			fill(0,0,0,255);
			text(btnNames[i], halfWay, yOff*.75f);
			translate(xWidthOffset, 0, 0);
		}
		popStyle();	popMatrix();			
	}//drawSolverButtons

	public void drawDispButtons(String[] btnNames, int cmpVal, boolean hiliteRow){
		float xWidthOffset = menuWidth/(1.0f * btnNames.length), halfWay;
		pushMatrix();pushStyle();
		strokeWeight(.5f);
		stroke(0,0,0,255);
		noFill();
		translate(-xOff*.5f, 0, 0);
		for(int i =0; i<btnNames.length;++i){
			halfWay = (xWidthOffset - textWidth(btnNames[i]))/2.0f;
			if(hiliteRow) {			
				if(i==cmpVal){	fill(100,255,255,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);} 
				else{    	  	fill(100,100,225,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);}}
			else if(i==cmpVal){fill(100,255,100,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);} 
			else {				fill(255,255,255,255);rect(0,0,xWidthOffset, yOff);	fill(0,0,0,255);}
			fill(0,0,0,255);
			text(btnNames[i], halfWay, yOff*.75f);
			translate(xWidthOffset, 0, 0);
		}
		popStyle();	popMatrix();			
	}//drawSolverButtons	
	
	public void drawMouseBox(){
		translate((width * menuWidthMult-10),0);
	    setColorValFill(showInfo ? gui_LightGreen : gui_DarkRed);
		rect(0,0,10, 10);
	}
	public void setFill(int[] clr, int alpha){fill(clr[0],clr[1],clr[2], alpha);}
	public void setStroke(int[] clr, int alpha){stroke(clr[0],clr[1],clr[2], alpha);}
	public void drawSideBarBooleans(){
		//draw booleans and their state
		translate(10,yOff);
		setColorValFill(gui_Black);
		text("Boolean Flags",0,-yOff*.25f);
		for(int i =0; i<numFlags; ++i){
			if(flags[i] ){													dispMenuTxt(trueFlagNames[i],flagColors[i], true);			}
			else {	if(trueFlagNames[i].equals(falseFlagNames[i])) {		dispMenuTxt(trueFlagNames[i],new int[]{180,180,180}, false);}	
					else {													dispMenuTxt(falseFlagNames[i],new int[]{0,255-flagColors[i][1],255-flagColors[i][2]}, true);}		
			}
		}		
	}//drawSideBarBooleans
	public void dispMenuTxt(String txt, int[] clrAra, boolean showSphere){
		setFill(clrAra, 255); 
		translate(xOff*.5f,yOff*.5f);
		if(showSphere){setStroke(clrAra, 255);		sphere(5);	} 
		else {	noStroke();		}
		translate(-xOff*.5f,yOff*.5f);
		text(""+txt,xOff,-yOff*.25f);	
	}
	public void drawMenuInfo(){
		translate(xOff*.5f,minSlvClkY,0);
		setFill(new int[]{0,0,0}, 255);
		text("Curr Solver Type : "+ dispSolverType(),0,-yOff*.15f);
		translate(0,yOff*.15f,0);
		drawButtons(slvBtnNames,solverType);
		translate(0,yOff*2,0);
		text("Currently displaying : "+ dispPatternType(),0,-yOff*.15f);
		translate(0,yOff*.15f,0);
		drawButtons(dispBtnNames,currDispType);
		translate(0,yOff*2,0);
		text("Current KF vals: k="+ String.format("%.5f",curKFVals[0])+" f="+ String.format("%.5f",curKFVals[1]),0,-yOff*.25f);
	}	
	//draw ui objects
	public void  drawSideBarData(){
		for(int i =0; i<numGuiObjs; ++i){
			guiObjs[i].draw();			
		}
	}//drawSideBarData
	
	public void drawSideBarMenu(){
		pushMatrix();pushStyle();
			drawMouseBox();						//click mse box for info display
		popStyle();	popMatrix();	
		pushMatrix();pushStyle();
			drawSideBarBooleans();				//toggleable booleans 
		popStyle();	popMatrix();	
		pushMatrix();pushStyle();			
			drawMenuInfo();						//what solver is currently being used, and buttons to select which one
		popStyle();	popMatrix();	
		pushMatrix();pushStyle();
			drawSideBarData();				//draw what user-modifiable fields are currently available
		popStyle();	popMatrix();			
		pushMatrix();pushStyle();
			drawSideBarKFData();				//display the values of the kf data for all integrators and styles
		popStyle();	popMatrix();			
	}
	public void drawSideBarKFData(){
		translate(xOff*.5f,maxGuiClkY+(2*yOff),0);
		setFill(new int[]{0,0,0}, 255);
		translate(0,yOff,0);
		text("KF vals for Integrators and Styles",0,-yOff*.25f);
		drawButtons(slvGridNames,solverType+1);//kfValStr
		translate(0,yOff,0);
		for(int i =0; i<kfValStr.length; i+=2){
			drawDispButtons(kfValStr[i],solverType+1, i==2*currDispType);
			translate(0,yOff,0);
			drawDispButtons(kfValStr[i+1],solverType+1, i==2*currDispType);
			translate(0,yOff,0);
		}
	}
	
	//draw side bar on left side of screen to enable interaction with booleans
	public void drawSideBar(){
		pushMatrix();pushStyle();
		hint(DISABLE_DEPTH_TEST);
		noLights();
		setColorValFill(gui_White);
		rect(0,0,width*menuWidthMult, height);
		drawSideBarMenu();
		hint(ENABLE_DEPTH_TEST);
		popStyle();	popMatrix();	
	}//drawSideBar

//	public void scribeHeaderRight(String S) {fill(0); text(S,width-7.5f*S.length(),20); noFill();} // writes black on screen top, right-aligned
//	public void displayHeader() { // Displays title and authors face on screen
//	    scribeHeaderRight("John Turner"); 
//	    image(jtFace,  width-125,25,100,100);
//	    }

	
	//project passed point onto box surface based on location - to help visualize the location in 3d
	public void drawProjOnBox(myPoint p){
		//myPoint[]  projOnPlanes = new myPoint[6];
		myPoint prjOnPlane;
		//public myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		pushMatrix();
		translate(-p.x,-p.y,-p.z);
		for(int i  = 0; i< 6; ++i){				
			prjOnPlane = bndChkInCntrdBox3D(intersectPl(p, boxNorms[i], boxWallPts[i][0],boxWallPts[i][1],boxWallPts[i][2]));				
			show(prjOnPlane,5,rgbClrs[i/2]);				
		}
		popMatrix();
	}//drawProjOnBox
	
	public myPoint bndChkInBox2D(myPoint p){p.set(Math.max(0,Math.min(p.x,grid2D_X)),Math.max(0,Math.min(p.y,grid2D_Y)),0);return p;}
	public myPoint bndChkInBox3D(myPoint p){p.set(Math.max(0,Math.min(p.x,gridDimX)), Math.max(0,Math.min(p.y,gridDimY)),Math.max(0,Math.min(p.z,gridDimZ)));return p;}	
	public myPoint bndChkInCntrdBox3D(myPoint p){
		p.set(Math.max(-hGDimX,Math.min(p.x,hGDimX)), 
				Math.max(-hGDimY,Math.min(p.y,hGDimY)),
				Math.max(-hGDimZ,Math.min(p.z,hGDimZ)));return p;}	
	 
	public void translate(double x, double y, double z){translate((float)x,(float)y,(float)z);}
	//************************************************************************
	//**** SPIRAL
	//************************************************************************
	//3d rotation - rotate P by angle a around point G and axis normal to plane IJ
	myPoint R(myPoint P, double a, myVector I, myVector J, myPoint G) {
		double x= myVector._dot(new myVector(G,P),U(I)), y=myVector._dot(new myVector(G,P),U(J)); 
		double c=Math.cos(a), s=Math.sin(a); 
		double iXVal = x*c-x-y*s, jYVal= x*s+y*c-y;
		
		return myPoint._add(P,iXVal,I,jYVal,J); }; 

	public myPoint PtOnSpiral(myPoint A, myPoint B, myPoint C, double t) {
		//center is coplanar to A and B, and coplanar to B and C, but not necessarily coplanar to A, B and C
		//so center will be coplanar to mp(A,B) and mp(B,C) - use mpCA midpoint to determine plane mpAB-mpBC plane?
		myPoint mAB = new myPoint(A,.5f, B);
		myPoint mBC = new myPoint(B,.5f, C);
		myPoint mCA = new myPoint(C,.5f, A);
		myVector mI = U(mCA,mAB);
		myVector mTmp = myVector._cross(mI,U(mCA,mBC));
		myVector mJ = U(mTmp._cross(mI));	//I and J are orthonormal
		double a =spiralAngle(A,B,B,C); 
		double s =spiralScale(A,B,B,C);
		
		//myPoint G = spiralCenter(a, s, A, B, mI, mJ); 
		myPoint G = spiralCenter(A, mAB, B, mBC); 
		return new myPoint(G, Math.pow(s,t), R(A,t*a,mI,mJ,G));
	  }
	
//	//s-cut to print to console
	public void pr(String str){outStr2Scr(str);}

	public double spiralAngle(myPoint A, myPoint B, myPoint C, myPoint D) {return myVector._angleBetween(new myVector(A,B),new myVector(C,D));}
	public double spiralScale(myPoint A, myPoint B, myPoint C, myPoint D) {return myPoint._dist(C,D)/ myPoint._dist(A,B);}
	
	public myPoint R(myPoint Q, myPoint C, myPoint P, myPoint R) { // returns rotated version of Q by angle(CP,CR) parallel to plane (C,P,R)
		myVector I0=U(C,P), I1=U(C,R), V=new myVector(C,Q); 
		double c=myPoint._dist(I0,I1), s=Math.sqrt(1.-(c*c)); 
		if(Math.abs(s)<0.00001) return Q;
		myVector J0=V(1./s,I1,-c/s,I0);  
		myVector J1=V(-s,I0,c,J0);  
		double x=V._dot(I0), y=V._dot(J0);  
		return P(Q,x,M(I1,I0),y,M(J1,J0)); 
	} 	
	// spiral given 4 points, AB and CD are edges corresponding through rotation
	public myPoint spiralCenter(myPoint A, myPoint B, myPoint C, myPoint D) {         // new spiral center
		myVector AB=V(A,B), CD=V(C,D), AC=V(A,C);
		double m=CD.magn/AB.magn, n=CD.magn*AB.magn;		
		myVector rotAxis = U(AB._cross(CD));		//expect ab and ac to be coplanar - this is the axis to rotate around to find f
		
		myVector rAB = myVector._rotAroundAxis(AB, rotAxis, PConstants.HALF_PI);
		double c=AB._dot(CD)/n, 
				s=rAB._dot(CD)/n;
		double AB2 = AB._dot(AB), a=AB._dot(AC)/AB2, b=rAB._dot(AC)/AB2;
		double x=(a-m*( a*c+b*s)), y=(b-m*(-a*s+b*c));
		double d=1+m*(m-2*c);  if((c!=1)&&(m!=1)) { x/=d; y/=d; };
		return P(P(A,x,AB),y,rAB);
	  }
	
	
	public void cylinder(myPoint A, myPoint B, float r, int c1, int c2) {
		myPoint P = A;
		myVector V = V(A,B);
		myVector I = c.drawSNorm;//U(Normal(V));
		myVector J = U(N(I,V));
		float da = TWO_PI/36;
		beginShape(QUAD_STRIP);
			for(float a=0; a<=TWO_PI+da; a+=da) {fill(c1); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,0,V)); fill(c2); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,1,V));}
		endShape();
	}
	
	//point functions
	public myPoint P() {return new myPoint(); };                                                                          // point (x,y,z)
	public myPoint P(double x, double y, double z) {return new myPoint(x,y,z); };                                            // point (x,y,z)
	public myPoint P(myPoint A) {return new myPoint(A.x,A.y,A.z); };                                                           // copy of point P
	public myPoint P(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public myPoint L(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public myPoint P(myPoint A, myPoint B) {return P((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                             // (A+B)/2
	public myPoint P(myPoint A, myPoint B, myPoint C) {return new myPoint((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };     // (A+B+C)/3
	public myPoint P(myPoint A, myPoint B, myPoint C, myPoint D) {return P(P(A,B),P(C,D)); };                                            // (A+B+C+D)/4
	public myPoint P(double s, myPoint A) {return new myPoint(s*A.x,s*A.y,s*A.z); };                                            // sA
	public myPoint A(myPoint A, myPoint B) {return new myPoint(A.x+B.x,A.y+B.y,A.z+B.z); };                                         // A+B
	public myPoint P(double a, myPoint A, double b, myPoint B) {return A(P(a,A),P(b,B));}                                        // aA+bB 
	public myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C) {return A(P(a,A),P(b,B,c,C));}                     // aA+bB+cC 
	public myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C, double d, myPoint D){return A(P(a,A,b,B),P(c,C,d,D));}   // aA+bB+cC+dD
	public myPoint P(myPoint P, myVector V) {return new myPoint(P.x + V.x, P.y + V.y, P.z + V.z); }                                 // P+V
	public myPoint P(myPoint P, double s, myVector V) {return new myPoint(P.x+s*V.x,P.y+s*V.y,P.z+s*V.z);}                           // P+sV
	public myPoint P(myPoint O, double x, myVector I, double y, myVector J) {return P(O.x+x*I.x+y*J.x,O.y+x*I.y+y*J.y,O.z+x*I.z+y*J.z);}  // O+xI+yJ
	public myPoint P(myPoint O, double x, myVector I, double y, myVector J, double z, myVector K) {return P(O.x+x*I.x+y*J.x+z*K.x,O.y+x*I.y+y*J.y+z*K.y,O.z+x*I.z+y*J.z+z*K.z);}  // O+xI+yJ+kZ
	void makePts(myPoint[] C) {for(int i=0; i<C.length; i++) C[i]=P();}

	
	void bezier(myPoint A, myPoint B, myPoint C, myPoint D) {bezier((float)A.x,(float)A.y,(float)A.z,(float)B.x,(float)B.y,(float)B.z,(float)C.x,(float)C.y,(float)C.z,(float)D.x,(float)D.y,(float)D.z);} // draws a cubic Bezier curve with control points A, B, C, D
	void bezier(myPoint [] C) {bezier(C[0],C[1],C[2],C[3]);} // draws a cubic Bezier curve with control points A, B, C, D
	myPoint bezierPoint(myPoint[] C, float t) {return P(bezierPoint((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierPoint((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierPoint((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }
	myVector bezierTangent(myPoint[] C, float t) {return V(bezierTangent((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierTangent((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierTangent((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }

	
	public myPoint Mouse() {return new myPoint(mouseX, mouseY,0);}                                          			// current mouse location
	public myVector MouseDrag() {return new myVector(mouseX-pmouseX,mouseY-pmouseY,0);};                     			// vector representing recent mouse displacement
	
	//public int color(myPoint p){return this.color((int)p.x,(int)p.z,(int)p.y);}	//needs to be x,z,y for some reason - to match orientation of color frames in z-up 3d geometry
	public int color(myPoint p){return this.color((int)p.x,(int)p.y,(int)p.z);}	
	
	// =====  vector functions
	public myVector V() {return new myVector(); };                                                                          // make vector (x,y,z)
	public myVector V(double x, double y, double z) {return new myVector(x,y,z); };                                            // make vector (x,y,z)
	public myVector V(myVector V) {return new myVector(V.x,V.y,V.z); };                                                          // make copy of vector V
	public myVector A(myVector A, myVector B) {return new myVector(A.x+B.x,A.y+B.y,A.z+B.z); };                                       // A+B
	public myVector A(myVector U, float s, myVector V) {return V(U.x+s*V.x,U.y+s*V.y,U.z+s*V.z);};                               // U+sV
	public myVector M(myVector U, myVector V) {return V(U.x-V.x,U.y-V.y,U.z-V.z);};                                              // U-V
	public myVector M(myVector V) {return V(-V.x,-V.y,-V.z);};                                                              // -V
	public myVector V(myVector A, myVector B) {return new myVector((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                      // (A+B)/2
	public myVector V(myVector A, float s, myVector B) {return new myVector(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };      // (1-s)A+sB
	public myVector V(myVector A, myVector B, myVector C) {return new myVector((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };  // (A+B+C)/3
	public myVector V(myVector A, myVector B, myVector C, myVector D) {return V(V(A,B),V(C,D)); };                                         // (A+B+C+D)/4
	public myVector V(double s, myVector A) {return new myVector(s*A.x,s*A.y,s*A.z); };                                           // sA
	public myVector V(double a, myVector A, double b, myVector B) {return A(V(a,A),V(b,B));}                                       // aA+bB 
	public myVector V(double a, myVector A, double b, myVector B, double c, myVector C) {return A(V(a,A,b,B),V(c,C));}                   // aA+bB+cC
	public myVector V(myPoint P, myPoint Q) {return new myVector(P,Q);};                                          // PQ
	public myVector N(myVector U, myVector V) {return V( U.y*V.z-U.z*V.y, U.z*V.x-U.x*V.z, U.x*V.y-U.y*V.x); };                  // UxV cross product (normal to both)
	public myVector N(myPoint A, myPoint B, myPoint C) {return N(V(A,B),V(A,C)); };                                                   // normal to triangle (A,B,C), not normalized (proportional to area)
	public myVector B(myVector U, myVector V) {return U(N(N(U,V),U)); }        

	public myVector U(myVector v){myVector u = new myVector(v); return u._normalize(); }
	public myVector U(myPoint a, myPoint b){myVector u = new myVector(a,b); return u._normalize(); }
	public myVector U(double x, double y, double z) {myVector u = new myVector(x,y,z); return u._normalize();}
	
	public myVector normToPlane(myPoint A, myPoint B, myPoint C) {return myVector._cross(new myVector(A,B),new myVector(A,C)); };   // normal to triangle (A,B,C), not normalized (proportional to area)

	public void gl_normal(myVector V) {normal((float)V.x,(float)V.y,(float)V.z);}                                          // changes normal for smooth shading
	public void gl_vertex(myPoint P) {vertex((float)P.x,(float)P.y,(float)P.z);}                                           // vertex for shading or drawing
	public void gl_normal(myVectorf V) {normal(V.x,V.y,V.z);}                                          // changes normal for smooth shading
	public void gl_vertex(myPointf P) {vertex(P.x,P.y,P.z);}                                           // vertex for shading or drawing
	public void showVec( myPoint ctr, double len, myVector v){line(ctr.x,ctr.y,ctr.z,ctr.x+(v.x)*len,ctr.y+(v.y)*len,ctr.z+(v.z)*len);}
	public void show(myPoint P, double r){show(P,r, gui_Black);}
	public void show(myPoint P, String s) {text(s, (float)P.x, (float)P.y, (float)P.z); } // prints string s in 3D at P
	public void show(myPoint P, double r, int clr, String txt) {
		pushMatrix(); pushStyle(); 
		if(clr!= -1){setColorValFill(clr); setColorValStroke(clr);}
		sphereDetail(5);
		translate((float)P.x,(float)P.y,(float)P.z); 
		sphere((float)r); 
		setColorValFill(gui_Black);setColorValStroke(gui_Black);
		double d = 1.1 * r;
		show(myPoint.ZEROPT, txt, new myVector(d,d,d));
		popStyle(); popMatrix();} // render sphere of radius r and center P)

	public void show(myPoint P, double r, int clr) {pushMatrix(); pushStyle(); if(clr!= -1){setColorValFill(clr); setColorValStroke(clr);}
			sphereDetail(5);translate((float)P.x,(float)P.y,(float)P.z); sphere((float)r); popStyle(); popMatrix();} // render sphere of radius r and center P)
	public void show(myPoint P, double r, String s, myVector D){show(P,r, gui_Black);pushStyle();setColorValFill(gui_Black);show(P,s,D);popStyle();}
	public void show(myPoint P, double r, String s, myVector D, int clr){show(P,r, clr);pushStyle();setColorValFill(clr);show(P,s,D);popStyle();}
	public void show(myPoint P, String s, myVector D) {text(s, (float)(P.x+D.x), (float)(P.y+D.y), (float)(P.z+D.z));  } // prints string s in 3D at P+D
	public void show(myPoint[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public void show(myPoint[] ara, myVector norm) {beginShape();gl_normal(norm); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public boolean intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C, myPoint X) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		X.set(myPoint._add(E,t,T));		return true;
	}	
	public myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		
		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		
		return (myPoint._add(E,t,T));		
	}	
	// if ray from E along V intersects sphere at C with radius r, return t when intersection occurs
	public double intersectPt(myPoint E, myVector V, myPoint C, double r) { 
		myVector Vce = V(C,E);
		double CEdCE = Vce._dot(Vce), VdV = V._dot(V), VdVce = V._dot(Vce), b = 2 * VdVce, c = CEdCE - (r*r),
				radical = (b*b) - 4 *(VdV) * c;
		if(radical < 0) return -1;
		double t1 = (b + Math.sqrt(radical))/(2*VdV), t2 = (b - Math.sqrt(radical))/(2*VdV);			
		return ((t1 > 0) && (t2 > 0) ? Math.min(t1, t2) : ((t1 < 0 ) ? ((t2 < 0 ) ? -1 : t2) : t1) );
		
	}	
	public final int  // set more colors using Menu >  Tools > Color Selector
	  black=0xff000000, 
	  white=0xffFFFFFF,
	  red=0xffFF0000, 
	  green=0xff00FF00, 
	  blue=0xff0000FF, 
	  yellow=0xffFFFF00, 
	  cyan=0xff00FFFF, 
	  magenta=0xffFF00FF,
	  grey=0xff818181, 
	  orange=0xffFFA600, 
	  brown=0xffB46005, 
	  metal=0xffB5CCDE, 
	  dgreen=0xff157901;
	//set color based on passed point r= x, g = z, b=y
	public void fillAndShowLineByRBGPt(myPoint p, float x,  float y, float w, float h){
		fill((int)p.x,(int)p.y,(int)p.z);
		stroke((int)p.x,(int)p.y,(int)p.z);
		rect(x,y,w,h);
		//show(p,r,-1);
	}
	
	public myPoint WrldToScreen(myPoint wPt){return new myPoint(screenX((float)wPt.x,(float)wPt.y,(float)wPt.z),screenY((float)wPt.x,(float)wPt.y,(float)wPt.z),screenZ((float)wPt.x,(float)wPt.y,(float)wPt.z));}

	public int[][] triColors = new int[][] {{gui_DarkMagenta,gui_DarkBlue,gui_DarkGreen,gui_DarkCyan},
                                                {gui_LightMagenta,gui_LightBlue,gui_LightGreen,gui_TransCyan}};
    public void setColorValFill(int colorVal){ setColorValFill(colorVal,255);}
	public void setColorValFill(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha);break;}
	    	case gui_White  			: { fill(255,255,255,alpha);break; }
	    	case gui_Gray   			: { fill(120,120,120,alpha); break;}
	    	case gui_Yellow 			: { fill(255,255,0,alpha);break; }
	    	case gui_Cyan   			: { fill(0,255,255,alpha);  break; }
	    	case gui_Magenta			: { fill(255,0,255,alpha);break; }
	    	case gui_Red    			: { fill(255,0,0,alpha); break; }
	    	case gui_Blue				: { fill(0,0,255,alpha); break; }
	    	case gui_Green				: { fill(0,255,0,alpha);  break; } 
	    	case gui_DarkGray   		: { fill(80,80,80,alpha); break;}
	    	case gui_DarkRed    		: { fill(120,0,0,alpha);break;}
	    	case gui_DarkBlue   		: { fill(0,0,120,alpha); break;}
	    	case gui_DarkGreen  		: { fill(0,120,0,alpha); break;}
	    	case gui_DarkYellow 		: { fill(120,120,0,alpha); break;}
	    	case gui_DarkMagenta		: { fill(120,0,120,alpha); break;}
	    	case gui_DarkCyan   		: { fill(0,120,120,alpha); break;}	   
	    	case gui_LightGray   		: { fill(200,200,200,alpha); break;}
	    	case gui_LightRed    		: { fill(255,110,110,alpha); break;}
	    	case gui_LightBlue   		: { fill(110,110,255,alpha); break;}
	    	case gui_LightGreen  		: { fill(110,255,110,alpha); break;}
	    	case gui_LightYellow 		: { fill(255,255,110,alpha); break;}
	    	case gui_LightMagenta		: { fill(255,110,255,alpha); break;}
	    	case gui_LightCyan   		: { fill(110,255,255,alpha); break;}    	
	    	case gui_Black			 	: { fill(0,0,0,alpha);break;}//
	    	case gui_TransBlack  	 	: { fill(0x00010100);  break;}//	have to use hex so that alpha val is not lost    	
	    	case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); break;}
	    	case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2);  break;}
	    	case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2);  break;}
	    	case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2);  break;}
	    	case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); break;}
	    	case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); break;}
	    	case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); break;}
	    	case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); break;}//
	    	case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2);  break;}
	    	case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2);  break;}
	    	case gui_TransGreen 	 	: { fill(0,255,0,alpha/2);  break;}
	    	case gui_TransYellow 	 	: { fill(255,255,0,alpha/2);break;}
	    	case gui_TransCyan  	 	: { fill(0,255,255,alpha/2);break;}
	    	case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2);break;}
	    	default         			: { fill(255,255,255,alpha);break;}  	    	
		}//switch	
	}//setcolorValFill
	public void setColorValStroke(int colorVal){ setColorValStroke(colorVal, 255);}
	public void setColorValStroke(int colorVal, int alpha){
		switch (colorVal){
	    	case gui_White  	 	    : { stroke(255,255,255,alpha); break; }
 	    	case gui_Gray   	 	    : { stroke(120,120,120,alpha); break;}
	    	case gui_Yellow      	    : { stroke(255,255,0,alpha); break; }
	    	case gui_Cyan   	 	    : { stroke(0,255,255,alpha); break; }
	    	case gui_Magenta	 	    : { stroke(255,0,255,alpha);  break; }
	    	case gui_Red    	 	    : { stroke(255,120,120,alpha); break; }
	    	case gui_Blue		 	    : { stroke(120,120,255,alpha); break; }
	    	case gui_Green		 	    : { stroke(120,255,120,alpha); break; }
	    	case gui_DarkGray    	    : { stroke(80,80,80,alpha); break; }
	    	case gui_DarkRed     	    : { stroke(120,0,0,alpha); break; }
	    	case gui_DarkBlue    	    : { stroke(0,0,120,alpha); break; }
	    	case gui_DarkGreen   	    : { stroke(0,120,0,alpha); break; }
	    	case gui_DarkYellow  	    : { stroke(120,120,0,alpha); break; }
	    	case gui_DarkMagenta 	    : { stroke(120,0,120,alpha); break; }
	    	case gui_DarkCyan    	    : { stroke(0,120,120,alpha); break; }	   
	    	case gui_LightGray   	    : { stroke(200,200,200,alpha); break;}
	    	case gui_LightRed    	    : { stroke(255,110,110,alpha); break;}
	    	case gui_LightBlue   	    : { stroke(110,110,255,alpha); break;}
	    	case gui_LightGreen  	    : { stroke(110,255,110,alpha); break;}
	    	case gui_LightYellow 	    : { stroke(255,255,110,alpha); break;}
	    	case gui_LightMagenta	    : { stroke(255,110,255,alpha); break;}
	    	case gui_LightCyan   		: { stroke(110,255,255,alpha); break;}		   
	    	case gui_Black				: { stroke(0,0,0,alpha); break;}
	    	case gui_TransBlack  		: { stroke(1,1,1,1); break;}	    	
	    	case gui_FaintGray 			: { stroke(120,120,120,250); break;}
	    	case gui_FaintRed 	 		: { stroke(110,0,0,alpha); break;}
	    	case gui_FaintBlue 	 		: { stroke(0,0,110,alpha); break;}
	    	case gui_FaintGreen 		: { stroke(0,110,0,alpha); break;}
	    	case gui_FaintYellow 		: { stroke(110,110,0,alpha); break;}
	    	case gui_FaintCyan  		: { stroke(0,110,110,alpha); break;}
	    	case gui_FaintMagenta  		: { stroke(110,0,110,alpha); break;}
	    	case gui_TransGray 	 		: { stroke(150,150,150,alpha/4); break;}
	    	case gui_TransRed 	 		: { stroke(255,0,0,alpha/2); break;}
	    	case gui_TransBlue 	 		: { stroke(0,0,255,alpha/2); break;}
	    	case gui_TransGreen 		: { stroke(0,255,0,alpha/2); break;}
	    	case gui_TransYellow 		: { stroke(255,255,0,alpha/2); break;}
	    	case gui_TransCyan  		: { stroke(0,255,255,alpha/2); break;}
	    	case gui_TransMagenta  		: { stroke(255,0,255,alpha/2); break;}
	    	default         			: { stroke(55,55,255,alpha); break; }
		}//switch	
	}//setcolorValStroke	
	
    public void setColorValFillAmb(int colorVal){ setColorValFillAmb(colorVal,255);}
	public void setColorValFillAmb(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha); ambient(120,120,120);break;}
	    	case gui_White  			: { fill(255,255,255,alpha); ambient(255,255,255); break; }
	    	case gui_Gray   			: { fill(120,120,120,alpha); ambient(120,120,120); break;}
	    	case gui_Yellow 			: { fill(255,255,0,alpha); ambient(255,255,0); break; }
	    	case gui_Cyan   			: { fill(0,255,255,alpha); ambient(0,255,alpha); break; }
	    	case gui_Magenta			: { fill(255,0,255,alpha); ambient(255,0,alpha); break; }
	    	case gui_Red    			: { fill(255,0,0,alpha); ambient(255,0,0); break; }
	    	case gui_Blue				: { fill(0,0,255,alpha); ambient(0,0,alpha); break; }
	    	case gui_Green				: { fill(0,255,0,alpha); ambient(0,255,0); break; } 
	    	case gui_DarkGray   		: { fill(80,80,80,alpha); ambient(80,80,80); break;}
	    	case gui_DarkRed    		: { fill(120,0,0,alpha); ambient(120,0,0); break;}
	    	case gui_DarkBlue   		: { fill(0,0,120,alpha); ambient(0,0,120); break;}
	    	case gui_DarkGreen  		: { fill(0,120,0,alpha); ambient(0,120,0); break;}
	    	case gui_DarkYellow 		: { fill(120,120,0,alpha); ambient(120,120,0); break;}
	    	case gui_DarkMagenta		: { fill(120,0,120,alpha); ambient(120,0,120); break;}
	    	case gui_DarkCyan   		: { fill(0,120,120,alpha); ambient(0,120,120); break;}		   
	    	case gui_LightGray   		: { fill(200,200,200,alpha); ambient(200,200,200); break;}
	    	case gui_LightRed    		: { fill(255,110,110,alpha); ambient(255,110,110); break;}
	    	case gui_LightBlue   		: { fill(110,110,255,alpha); ambient(110,110,alpha); break;}
	    	case gui_LightGreen  		: { fill(110,255,110,alpha); ambient(110,255,110); break;}
	    	case gui_LightYellow 		: { fill(255,255,110,alpha); ambient(255,255,110); break;}
	    	case gui_LightMagenta		: { fill(255,110,255,alpha); ambient(255,110,alpha); break;}
	    	case gui_LightCyan   		: { fill(110,255,255,alpha); ambient(110,255,alpha); break;}	    	
	    	case gui_Black			 	: { fill(0,0,0,alpha); ambient(0,0,0); break;}//
	    	case gui_TransBlack  	 	: { fill(0x00010100); ambient(0,0,0); break;}//	have to use hex so that alpha val is not lost    	
	    	case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); ambient(77,77,77); break;}//
	    	case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2); ambient(110,0,0); break;}//
	    	case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2); ambient(0,0,110); break;}//
	    	case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2); ambient(0,110,0); break;}//
	    	case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); ambient(110,110,0); break;}//
	    	case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); ambient(0,110,110); break;}//
	    	case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); ambient(110,0,110); break;}//
	    	case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); ambient(120,120,120); break;}//
	    	case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2); ambient(255,0,0); break;}//
	    	case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2); ambient(0,0,alpha); break;}//
	    	case gui_TransGreen 	 	: { fill(0,255,0,alpha/2); ambient(0,255,0); break;}//
	    	case gui_TransYellow 	 	: { fill(255,255,0,alpha/2); ambient(255,255,0); break;}//
	    	case gui_TransCyan  	 	: { fill(0,255,255,alpha/2); ambient(0,255,alpha); break;}//
	    	case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2); ambient(255,0,alpha); break;}//   	
	    	default         			: { fill(255,255,255,alpha); ambient(255,255,alpha); break; }	    	    	
		}//switch	
	}//setcolorValFill
	
	//returns one of 30 predefined colors as an array (to support alpha)
	public int[] getClr(int colorVal){		return getClr(colorVal, 255);	}//getClr
	public int[] getClr(int colorVal, int alpha){
		switch (colorVal){
    	case gui_Gray   		         : { return new int[] {120,120,120,alpha}; }
    	case gui_White  		         : { return new int[] {255,255,255,alpha}; }
    	case gui_Yellow 		         : { return new int[] {255,255,0,alpha}; }
    	case gui_Cyan   		         : { return new int[] {0,255,255,alpha};} 
    	case gui_Magenta		         : { return new int[] {255,0,255,alpha};}  
    	case gui_Red    		         : { return new int[] {255,0,0,alpha};} 
    	case gui_Blue			         : { return new int[] {0,0,255,alpha};}
    	case gui_Green			         : { return new int[] {0,255,0,alpha};}  
    	case gui_DarkGray   	         : { return new int[] {80,80,80,alpha};}
    	case gui_DarkRed    	         : { return new int[] {120,0,0,alpha};}
    	case gui_DarkBlue  	 	         : { return new int[] {0,0,120,alpha};}
    	case gui_DarkGreen  	         : { return new int[] {0,120,0,alpha};}
    	case gui_DarkYellow 	         : { return new int[] {120,120,0,alpha};}
    	case gui_DarkMagenta	         : { return new int[] {120,0,120,alpha};}
    	case gui_DarkCyan   	         : { return new int[] {0,120,120,alpha};}	   
    	case gui_LightGray   	         : { return new int[] {200,200,200,alpha};}
    	case gui_LightRed    	         : { return new int[] {255,110,110,alpha};}
    	case gui_LightBlue   	         : { return new int[] {110,110,255,alpha};}
    	case gui_LightGreen  	         : { return new int[] {110,255,110,alpha};}
    	case gui_LightYellow 	         : { return new int[] {255,255,110,alpha};}
    	case gui_LightMagenta	         : { return new int[] {255,110,255,alpha};}
    	case gui_LightCyan   	         : { return new int[] {110,255,255,alpha};}
    	case gui_Black			         : { return new int[] {0,0,0,alpha};}
    	case gui_FaintGray 		         : { return new int[] {110,110,110,alpha};}
    	case gui_FaintRed 	 	         : { return new int[] {110,0,0,alpha};}
    	case gui_FaintBlue 	 	         : { return new int[] {0,0,110,alpha};}
    	case gui_FaintGreen 	         : { return new int[] {0,110,0,alpha};}
    	case gui_FaintYellow 	         : { return new int[] {110,110,0,alpha};}
    	case gui_FaintCyan  	         : { return new int[] {0,110,110,alpha};}
    	case gui_FaintMagenta  	         : { return new int[] {110,0,110,alpha};}    	
    	case gui_TransBlack  	         : { return new int[] {1,1,1,alpha/2};}  	
    	case gui_TransGray  	         : { return new int[] {110,110,110,alpha/2};}
    	case gui_TransLtGray  	         : { return new int[] {180,180,180,alpha/2};}
    	case gui_TransRed  	         	 : { return new int[] {110,0,0,alpha/2};}
    	case gui_TransBlue  	         : { return new int[] {0,0,110,alpha/2};}
    	case gui_TransGreen  	         : { return new int[] {0,110,0,alpha/2};}
    	case gui_TransYellow  	         : { return new int[] {110,110,0,alpha/2};}
    	case gui_TransCyan  	         : { return new int[] {0,110,110,alpha/2};}
    	case gui_TransMagenta  	         : { return new int[] {110,0,110,alpha/2};}	
    	case gui_TransWhite  	         : { return new int[] {220,220,220,alpha/2};}	
    	default         		         : { return new int[] {255,255,255,alpha};}    
		}//switch
	}//getClr
	
	public int getRndClrInt(){return (int)random(0,23);}		//return a random color flag value from below
	public int[] getRndClr(int alpha){return new int[]{(int)random(0,255),(int)random(0,255),(int)random(0,255),alpha};	}
	public int[] getRndClr(){return getRndClr(255);	}		
	public Integer[] getClrMorph(int a, int b, double t){return getClrMorph(getClr(a), getClr(b), t);}    
	public Integer[] getClrMorph(int[] a, int[] b, double t){
		if(t==0){return new Integer[]{a[0],a[1],a[2],a[3]};} else if(t==1){return new Integer[]{b[0],b[1],b[2],b[3]};}
		return new Integer[]{(int)(((1.0f-t)*a[0])+t*b[0]),(int)(((1.0f-t)*a[1])+t*b[1]),(int)(((1.0f-t)*a[2])+t*b[2]),(int)(((1.0f-t)*a[3])+t*b[3])};
	}

	//used to generate random color
	public static final int gui_rnd = -1;
	//color indexes
	public static final int gui_Black 	= 0;
	public static final int gui_White 	= 1;	
	public static final int gui_Gray 	= 2;
	
	public static final int gui_Red 	= 3;
	public static final int gui_Blue 	= 4;
	public static final int gui_Green 	= 5;
	public static final int gui_Yellow 	= 6;
	public static final int gui_Cyan 	= 7;
	public static final int gui_Magenta = 8;
	
	public static final int gui_LightRed = 9;
	public static final int gui_LightBlue = 10;
	public static final int gui_LightGreen = 11;
	public static final int gui_LightYellow = 12;
	public static final int gui_LightCyan = 13;
	public static final int gui_LightMagenta = 14;
	public static final int gui_LightGray = 15;

	public static final int gui_DarkCyan = 16;
	public static final int gui_DarkYellow = 17;
	public static final int gui_DarkGreen = 18;
	public static final int gui_DarkBlue = 19;
	public static final int gui_DarkRed = 20;
	public static final int gui_DarkGray = 21;
	public static final int gui_DarkMagenta = 22;
	
	public static final int gui_FaintGray = 23;
	public static final int gui_FaintRed = 24;
	public static final int gui_FaintBlue = 25;
	public static final int gui_FaintGreen = 26;
	public static final int gui_FaintYellow = 27;
	public static final int gui_FaintCyan = 28;
	public static final int gui_FaintMagenta = 29;
	
	public static final int gui_TransBlack = 30;
	public static final int gui_TransGray = 31;
	public static final int gui_TransMagenta = 32;	
	public static final int gui_TransLtGray = 33;
	public static final int gui_TransRed = 34;
	public static final int gui_TransBlue = 35;
	public static final int gui_TransGreen = 36;
	public static final int gui_TransYellow = 37;
	public static final int gui_TransCyan = 38;	
	public static final int gui_TransWhite = 39;	
}
	