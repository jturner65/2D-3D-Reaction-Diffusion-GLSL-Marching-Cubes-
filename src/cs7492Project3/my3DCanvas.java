package cs7492Project3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import processing.core.PConstants;
import processing.core.PMatrix3D;
import processing.opengl.PGL;
import processing.opengl.PGraphics3D;

public class my3DCanvas {
	public cs7492Proj3 p;
	
	public myPoint drawEyeLoc,													//rx,ry,dz coords where eye was when drawing - set when first drawing and return eye to this location whenever trying to draw again - rx,ry,dz
		scrCtrInWorld,mseLoc, eyeInWorld, oldMseLoc, distMsePt, mseIn3DBox;
	private myPoint dfCtr;											//mouse location projected onto current drawing canvas

	public edge camEdge;												//denotes line perp to cam eye, to use for intersections for mouse selection
	public final float canvasDim = 15000; 									//canvas dimension for "virtual" 3d		
	private myPoint[] canvas3D;											//3d plane, normal to camera eye, to be used for drawing - need to be in "view space" not in "world space", so that if camera moves they don't change
	public myVector eyeToMse,											//eye to 2d mouse location 
					eyeToCtr,													//vector from eye to center of cube, to be used to determine which panels of bounding box to show or hide
					eyeTodfCtr,
	//				canvasNorm, 												//normal of eye-to-mouse toward scene, current drawn object's normal to canvas
					drawSNorm;													//current normal of viewport/screen
		
	public int viewDimW, viewDimH;
	public float curDepth;
	public final float TQTR_PI;
	public my3DCanvas(cs7492Proj3 _p) {
		p = _p;
		viewDimW = p.width; viewDimH = p.height;
		curDepth = -1;
		TQTR_PI = PConstants.HALF_PI + PConstants.QUARTER_PI;
		initCanvas();
	}
	
	public void initCanvas(){
		canvas3D = new myPoint[4];		//3 points to define canvas
		canvas3D[0]=new myPoint();canvas3D[1]=new myPoint();canvas3D[2]=new myPoint();canvas3D[3]=new myPoint();
		drawEyeLoc = new myPoint(-1, -1, -1000);
		eyeInWorld = new myPoint();		
		scrCtrInWorld = new myPoint();									//
		mseLoc = new myPoint();
		eyeInWorld = new myPoint();
		oldMseLoc  = new myPoint();
		mseIn3DBox = new myPoint();
		distMsePt = new myPoint();
		dfCtr = new myPoint();											//mouse location projected onto current drawing canvas
		camEdge = new edge(p);	
		eyeToMse = new myVector();		
		eyeToCtr = new myVector();	
		eyeTodfCtr = new myVector();
		drawSNorm = new myVector();	
	//	canvasNorm = new myVector(); 						//normal of eye-to-mouse toward scene, current drawn object's normal to canvas	
	}

		//find points to define plane normal to camera eye, at set distance from camera, to use drawing canvas 	
	public void buildCanvas(){	
		float rawCtrDepth = getDepth(viewDimW/2, viewDimH/2);
		myPoint rawScrCtrInWorld = pick(viewDimW/2, viewDimH/2,rawCtrDepth);		
		myVector A = new myVector(rawScrCtrInWorld,  pick(viewDimW-10, 10,rawCtrDepth)),	B = new myVector(rawScrCtrInWorld,  pick(viewDimW-10, viewDimH-10,rawCtrDepth));	//ctr to upper right, ctr to lower right		
		drawSNorm = myVector._cross(A,B)._normalize();
		//build plane using norm - have canvas go through canvas ctr in 3d
		myVector planeTan = myVector._cross(drawSNorm, myVector._normalize(new myVector(drawSNorm.x+10000,drawSNorm.y+10,drawSNorm.z+10)))._normalize();			//result of vector crossed with normal will be in plane described by normal
     	myPoint lastPt = new myPoint(myPoint._add(new myPoint(), .707 * canvasDim, planeTan));
     	planeTan = myVector._rotAroundAxis(planeTan, drawSNorm, TQTR_PI);
		for(int i =0;i<canvas3D.length;++i){		//build invisible canvas to draw upon
     		canvas3D[i].set(myPoint._add(lastPt, canvasDim, planeTan));
     		//planeTan = myVector._cross(planeTan, drawSNorm)._normalize();												//this effectively rotates around center point by 90 degrees -builds a square
     		planeTan = myVector._rotAroundAxis(planeTan, drawSNorm);
     		//p.show(canvas3D[i],5,"i="+i,p.V(10,10,10));
     		lastPt = canvas3D[i];
     	}

		//normal to canvas through eye moved far behind viewer
		eyeInWorld =pick(viewDimW/2, viewDimH/2,-.00001f);
		//eyeInWorld =myPoint._add(rawScrCtrInWorld, myPoint._dist( pick(0,0,-1), rawScrCtrInWorld), drawSNorm);								//location of "eye" in world space
		eyeToCtr.set(eyeInWorld, rawScrCtrInWorld);
		scrCtrInWorld = getPlInterSect(rawScrCtrInWorld, myVector._normalize(eyeToCtr));
		
		float ctrDepth = p.screenZ((float)scrCtrInWorld.x, (float)scrCtrInWorld.y, (float)scrCtrInWorld.z);
		mseLoc = MouseScr(ctrDepth);	
		eyeToMse.set(eyeInWorld, mseLoc);		//unit vector in world coords of "eye" to mouse location
		eyeToMse._normalize();
		oldMseLoc.set(dfCtr);
		dfCtr = getPlInterSect(mseLoc, eyeToMse);
		
		mseIn3DBox = new myPoint(dfCtr.x+p.gridDimX2,dfCtr.y+p.gridDimY2,dfCtr.z+p.gridDimZ2);
		distMsePt = new myPoint(dfCtr,myVector._mult(drawSNorm, -1000));

    	
    	drawMseEdge();																//draw mouse location and text on canvas or in 3d world
    	drawCanvas();
	}//buildCanvas()

	public void drawCanvas(){
		p.noLights();
		p.pushMatrix();p.pushStyle();
		p.beginShape(p.QUAD);
		p.fill(255,255,255,80);
		//p.noStroke();
		p.gl_normal(eyeToMse);
     	//for(int i =0;i<canvas3D.length;++i){		//build invisible canvas to draw upon
        for(int i =canvas3D.length-1;i>=0;--i){		//build invisible canvas to draw upon
     		//p.line(canvas3D[i], canvas3D[(i+1)%canvas3D.length]);
     		p.gl_vertex(canvas3D[i]);
     	}
     	p.endShape(p.CLOSE);
     	p.popStyle();p.popMatrix();
     	p.lights();
	}
	
	//find pt in drawing plane that corresponds with point and camera eye normal
	public myPoint getPlInterSect(myPoint pt, myVector unitT){
		myPoint dctr = new myPoint(0,0,0);	//actual click location on visible plane
		 // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		p.intersectPl(pt, unitT, canvas3D[0],canvas3D[1],canvas3D[2],  dctr);//find point where mouse ray intersects canvas
		return dctr;		
	}//getPlInterSect	
	public myPoint getMseLoc(){return new myPoint(dfCtr);	}
	public myPoint getOldMseLoc(){return new myPoint(oldMseLoc);	}

	public myPoint getMseLoc(myPoint glbTrans){return myPoint._sub(dfCtr, glbTrans);	}
	public myPoint getOldMseLoc(myPoint glbTrans){return myPoint._sub(oldMseLoc, glbTrans);	}
	
	public float getDepth(int mX, int mY){
		PGL pgl = p.beginPGL();
		FloatBuffer depthBuffer = ByteBuffer.allocateDirect(1 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
		int newMy = viewDimH - mY;		pgl.readPixels(mX, newMy - 1, 1, 1, PGL.DEPTH_COMPONENT, PGL.FLOAT, depthBuffer);
		float depthValue = depthBuffer.get(0);
		p.endPGL();
		return depthValue;
	}
	
	public myPoint pick(int mX, int mY, float depth){
		int newMy = viewDimH - mY;
		float depthValue = depth;
		if(depth == -1){depthValue = getDepth( mX,  mY); }	
		//get 3d matrices
		PGraphics3D p3d = (PGraphics3D)p.g;
		PMatrix3D proj = p3d.projection.get();
		PMatrix3D modelView = p3d.modelview.get();
		PMatrix3D modelViewProjInv = proj; modelViewProjInv.apply( modelView ); modelViewProjInv.invert();	  
		float[] viewport = {0, 0, viewDimW, viewDimH};
		float[] normalized = new float[4];
		normalized[0] = ((mX - viewport[0]) / viewport[2]) * 2.0f - 1.0f;
		normalized[1] = ((newMy - viewport[1]) / viewport[3]) * 2.0f - 1.0f;
		normalized[2] = depthValue * 2.0f - 1.0f;
		normalized[3] = 1.0f;	  
		float[] unprojected = new float[4];	  
		modelViewProjInv.mult( normalized, unprojected );
		myPoint pickLoc = new myPoint( unprojected[0]/unprojected[3], unprojected[1]/unprojected[3], unprojected[2]/unprojected[3] );
		//p.outStr2Scr("Depth Buffer val : "+String.format("%.4f",depthValue)+ " for mx,my : ("+mX+","+mY+") and world loc : " + pickLoc.toStrBrf());
		return pickLoc;
	}		
	//hold depth when clicked
	public void holdMsDepth(){curDepth = getDepth(p.mouseX,p.mouseY);}
	public void clearMsDepth(){curDepth = -1;}
	public myPoint MouseScr(float depth) {return pick(p.mouseX,p.mouseY,depth);} 	
	public myPoint MouseScr() {return MouseScr(curDepth);} 	
	
	public void drawMseEdge(){//draw mouse sphere and edge normal to cam eye through mouse sphere 
		p.pushMatrix();	p.pushStyle();
			p.strokeWeight(1f);
			p.stroke(255,0,255,255);
			//c.camEdge.set(1000, c.eyeToMse, c.dfCtr);		//build edge through mouse point normal to camera eye	
			camEdge.set(eyeInWorld, dfCtr);		//build edge through mouse point and eye location in world	
			camEdge.drawMe();
			//p.outStr2Scr(camEdge.toString());
			p.translate((float)dfCtr.x, (float)dfCtr.y, (float)dfCtr.z);
			//project mouse point on bounding box walls
			if(p.flags[p.show3D]){p.drawProjOnBox(dfCtr);}
			p.drawAxes(10000,1f, myPoint.ZEROPT, 100, true);//
			//draw intercept with box
			p.stroke(0,0,0,255);
			p.show(myPoint.ZEROPT,3);
			p.drawText(""+dfCtr,4, 15, 4,0);
			p.scale(1.5f,1.5f,1.5f);
			//drawText(""+text_value_at_Cursor,4, -8, 4,0);getMseLoc(sceneCtrVals[sceneIDX])
			p.popStyle();
			p.popMatrix();		
	}//drawMseEdge		
}
//line bounded by verts - from a to b new myPoint(x,y,z); 
class edge{ 
	public cs7492Proj3 p;
	public myPoint a, b;
	public edge (cs7492Proj3 _p){this(_p,new myPoint(0,0,0),new myPoint(0,0,0));}
	public edge (cs7492Proj3 _p, myPoint _a, myPoint _b){p = _p;a=new myPoint(_a); b=new myPoint(_b);}
	public void set(float d, myVector dir, myPoint _p){	set( myPoint._add(_p,-d,new myVector(dir)), myPoint._add(_p,d,new myVector(dir)));} 
	public void set(myPoint _a, myPoint _b){a=new myPoint(_a); b=new myPoint(_b);}
	public myVector v(){return new myVector(b.x-a.x, b.y-a.y, b.z-a.z);}			//vector from a to b
	public myVector dir(){return v()._normalize();}
	public double len(){return  myPoint._dist(a,b);}
	public double distFromPt(myPoint P) {return myVector._det3(dir(),new myVector(a,P)); };
	public void drawMe(){//p.show(a, 4);p.show(b, 4);
		p.line(a.x,a.y,a.z,b.x,b.y,b.z); }
    public String toString(){return "a:"+a+" to b:"+b+" len:"+len();}
}
