package cs7492Project3;

import processing.core.PConstants;

public class myCell {
	public cs7492Proj3 p;
	public myRDSolver rs;
	public final int numConcSteps = 4;
	public final int x, y, z;			//x and y position in cell grid
	public final int xSize, ySize, zSize;  //size in pixels for cell in x y and z directions
	public final int offX, offY, offZ;	//pixel offset in x and y for upper left corner
	public float[][] conc;			//to handle multiple sequential values of concentration, for intermediate steps in integration
	public float uvv;		//calcualted uvv at this particular cell - only used for debugging
	public float newReactU;		//calcualted reactive element of chem U at this particular cell - only used for debugging
	public float newReactV;		//calcualted reactive element of chem V at this particular cell - only used for debugging
	
	public myCell(cs7492Proj3 _p, myRDSolver _rs, int _x, int _y){
		p = _p;
		rs = _rs;
		x = _x;
		y = _y;
		z = 0;
		xSize = rs.cell2dSize;		
		ySize = rs.cell2dSize;
		zSize = rs.cell2dSize;
		offX = x * xSize;
		offY = y * ySize;
		offZ = z * zSize;
		//concentrations start out 0
		conc = new float[2][numConcSteps];//first index is chemical, 2nd index is step in integration
		conc[rs.chemU][0] = 1;
		conc[rs.chemV][0] = 0;
		for(int i = 0; i<2; ++i){//initialize all cells to have concentration of 0 for chem 0 (v) and 1 for chem 1 (u)
			for(int j = 1; j < numConcSteps; ++j){ conc[i][j] = 0;	}
		}
		
	}//constructor
	
	/**
	 * this will update the concentration at the end of the integration step, adding the reactive and diffusive concentrations to the old concentration
	 * @param idx the index of the final concentration, to be moved to idx 0
	 */
	public void updateConcentration(int idx){
		for (int i = 0; i<2; ++i){
			conc[i][0] += conc[i][idx];
			if (conc[i][0] < 0){conc[i][0] = 0;}
			conc[i][idx] = 0;
		}
	}//updateConcentration
	/**
	 * this will update the concentration at the end of the integration step in ADI, replacing the value at idx 0
	 * @param idx the index of the final concentration, to be moved to idx 0
	 */
	public void updateConcentrationIMP(int idx){
		for (int i = 0; i<2; ++i){
			conc[i][0] = conc[i][idx];
			if (conc[i][0] < 0){conc[i][0] = 0;}
			conc[i][idx] = 0;
		}
	}//updateConcentration

	/**
	 * adds a value to a particular concentration value
	 * @param type whether chem u or v
	 * @param idx what index in calculation progression
	 * @param val the value to add to the current value
	 */
	public void addConc(int type, int idx, float val){conc[type][idx] += val;}
	
	
	/**
	 * draw a particular cell as a rectangle of a particular color based on the
	 * concentration of the chemical of interest, either A or B
	 */
	public void draw(int viewVal) {
		p.pushMatrix();p.pushStyle();
		p.fill(viewVal,viewVal,viewVal,255);
		p.noStroke();

		p.translate(offX, offY);
		p.beginShape(PConstants.QUADS);
		p.vertex(0, 0);
		p.vertex(0, ySize);
		p.vertex(xSize, ySize);
		p.vertex(xSize, 0);
		p.endShape(PConstants.CLOSE);
		p.popStyle();p.popMatrix();
	}// drawCell
	
	
	
	/**
	 *  calculates the reactive element of the concentrations of the cell
	 * @param f f-value for gray-scott reactive function
	 * @param k k-value for gray-scott reactive 
	 * @param deltaT time stepp
	 * @param diffIDX idx of diffusion element - 1 for pure forward euler, 2 for ADI
	 */
	public void calcReactive(float f, float k, float deltaT, int diffIDX){
		uvv = conc[rs.chemU][0] * conc[rs.chemV][0] * conc[rs.chemV][0];
		//if ((conc[rs.chemU][0] == 0) && (conc[rs.chemV][0] == 0)){System.out.println("danger, no concentration values in reactive calculation");}
		newReactU = deltaT * ((f  * (1 - conc[rs.chemU][0])) - uvv); 
		newReactV = deltaT * (uvv - ((f+k) * conc[rs.chemV][0])); 
		addConc(rs.chemU, diffIDX, newReactU);
		addConc(rs.chemV, diffIDX, newReactV);
	}
	
	//public float getConc(int type, int idx){return conc[type][idx];}
	public void setConc(int type, int idx, float val){conc[type][idx] = val;}
	
	public String toString(){
		String result = "";
		result += "x : " + x + " | y : " + y + " | concU : " + conc[rs.chemU][0] + " | concV : " + conc[rs.chemV][0]; 
		result += "\nReactive Values : uvv : " + uvv + " | most recent reactive U : " + newReactU + " | most recent reactive V : " + newReactV;
		
		return result;
	}
	
}//myCell class
