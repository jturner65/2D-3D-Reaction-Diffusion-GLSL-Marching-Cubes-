package cs7492Project3;

/**
 * this will describe a particular stencil for use in a diffusion calculation
 * @author John
 *
 */
public class myStencil {
	//public cs7492Proj3 p3;
	public myRDSolver rs;
	public int width;
	public int height;
	public float[][] multAra;
	//private boolean triDiag;
	
	public myStencil(myRDSolver _rs, int _w, int _h){
		rs = _rs;
		width = _w;
		height = _h;
		multAra = new float[width][height];
		//triDiag = false;
	}//2 element constructor

	/**
	 * set this stencil with the passed 2-d array
	 * @param valAra the array to set the current stencil to
	 */	
	public void setStencil(float[][] valAra){
		if ((valAra.length == width) && (valAra[0].length == height)){
			multAra = valAra;
		} else {
			System.out.println("passed val ara " + valAraToString(valAra) +  " not correct size for stencil : " + toString());
		}
	}//setStencil
	
	public String valAraToString(float[][] valAra){
		String result = "";
		for (int yVal = 0; yVal < valAra[0].length; ++yVal){
			result += ( (yVal == 0) ? ("{") : (""));
			for(int xVal = 0; xVal < valAra.length; ++xVal){
				result += ( (xVal == 0) ? ("{") : (""));
				result += "(" + xVal +"," + yVal  + ") = " + valAra[xVal][yVal];
				result += ( (xVal == valAra.length -1) ? ("}") : (","));
			}//for each column val
			result +=  ( (yVal == valAra[0].length - 1) ? ("}\n") : (",\n"));
		
		}//for each row
		return result;
	}
	
	/**
	 * calculate this particular stencil's diffusion effect on the passed cell's 2 chemicals using a torroidal mapping
	 * @param cell the cell to work on
	 * @param cellGrid the structure holding the cells being worked on
	 * @param Ku diffusion rate of chemical u
	 * @param Kv diffusion rate of chemical v
	 * @param updateIDX the index in the cell's concentration array to update
	 * @param deltaT the time increment
	 */
	public void calcValueFETorroid(myCell cell, myCellGrid cellGrid,  float Ku, float Kv, int updateIDX, float deltaT){
		//set grid2D_X and grid2D_Y to be values of highest index in x and y, respectively
		int gridWidth = cellGrid.gridWidth;
		int gridHeight = cellGrid.gridHeight;
		int gridWidthIDX = gridWidth - 1;
		int gridHeightIDX = gridHeight - 1;
		//only deal with cell directly through cellgrid
		int cellX = cell.x;
		int cellY = cell.y;
		float newDiffU = 0, newDiffV = 0, tmpCalcU = 0, tmpCalcV = 0;
		myCell tmpCell;
		
		//build array of indexes to use into the grid to find the neighbors of the cell to apply the stencil upon
		int[] xIDXara = new int[width];
		int[] yIDXara = new int[height];
		//System.out.print("X idx ara:");
		for (int i = 0; i < width; ++i){
			int newIDX = cellX - (width/2) + i;
			if (newIDX < 0){ newIDX += gridWidth; }
			if (newIDX > gridWidthIDX){ newIDX -= gridWidth;}
			xIDXara[i] = newIDX;
			//System.out.print(i + ":" + xIDXara[i] +" | ");
		}
		//System.out.println();
		
		//System.out.print("Y idx ara:");		
		for (int i = 0; i < height; ++i){
			int newIDX = cellY - (height/2) + i;
			if (newIDX < 0){ newIDX += gridHeight; }
			if (newIDX > gridHeightIDX){ newIDX -= gridHeight;}			
			yIDXara[i] = newIDX;
			//System.out.print(i + ":" + yIDXara[i] +" | ");
		}
		//System.out.println();
		
		//calc new diffusion values by applying stencil to neighbors
		for (int i = 0; i < width; ++i){
			for (int j = 0; j < height; ++j){
				if (multAra[i][j] != 0){
					tmpCell = cellGrid.cellMap[xIDXara[i]][yIDXara[j]];
					tmpCalcU = tmpCell.conc[rs.chemU][0];//getConc(rs.chemU, 0);
					tmpCalcV = tmpCell.conc[rs.chemV][0];//getConc(rs.chemV, 0);
					newDiffU += (tmpCalcU * multAra[i][j]); 				
					newDiffV += (tmpCalcV * multAra[i][j]); 	
				}
			}//for j - grid2D_Y			
		}//for i - grid2D_X	
		newDiffU *= (Ku * deltaT);
		newDiffV *= (Kv * deltaT);
		cellGrid.setConcAtCell(cellX, cellY, rs.chemU, updateIDX, newDiffU);
		cellGrid.setConcAtCell(cellX, cellY, rs.chemV, updateIDX, newDiffV);
	}//calcValueFETorroid
	
	/**
	 * calculate this particular stencil's diffusion effect on the passed cell's 2 chemicals using a Neumann mapping (no wrapping)
	 * @param cell the cell to work on
	 * @param cellGrid the structure holding the cells being worked on
	 * @param Ku diffusion rate of chemical u
	 * @param Kv diffusion rate of chemical v
	 * @param updateIDX the index in the cell's concentration array to update
	 * @param deltaT the time increment
	 */
	public void calcValueFENeumann(myCell cell, myCellGrid cellGrid, float Ku, float Kv, int updateIDX, float deltaT){
		//set grid2D_X and grid2D_Y to be values of highest index in x and y, respectively
		float newDiffU = 0, newDiffV = 0, tmpCalcU = 0, tmpCalcV = 0;
		myCell tmpCell;
		
		int calcWidth = width;
		int calcHeight = height;
		
		//start index for grid2D_X and grid2D_Y calculation - to cover boundaries 
		int widthStartIDX = 0;
		int heightStartIDX = 0;
		
		//amount to add to center of stencil due to calculations happening at boundaries - should never be more than 2
		float cntrMod = 0;
		
		//build array of indexes to use into the grid to find the neighbors of the cell to apply the stencil upon - hard boundaries - no wrapping
		int[] xIDXara = new int[width];
		int[] yIDXara = new int[height];
		for (int i = 0; i < width; ++i){
			int newIDX = cell.x - (width/2) + i;
			if (newIDX < 0){
				widthStartIDX++;
				cntrMod++;
				newIDX = -1;//dummy index, ignored				
			} //subtract from center multiplier, modify grid2D_X of stencil for this operation
			if (newIDX > cellGrid.gridWidth - 1){ 
				calcWidth--;
				newIDX = -1;
				cntrMod++;
			}	//subtract from center multiplier, modify grid2D_X of stencil for this operation
			xIDXara[i] = newIDX;
		}
		for (int i = 0; i < height; ++i){
			int newIDX = cell.y - (height/2) + i;
			if (newIDX < 0){ 
				heightStartIDX++;
				newIDX = -1;
				cntrMod++;
			} //subtract from center multiplier, modify grid2D_Y of stencil for this operation
			if (newIDX > cellGrid.gridHeight - 1){ 
				calcHeight--;
				newIDX = -1;
				cntrMod++;
			}	//subtract from center multiplier, modify grid2D_Y of stencil for this operation		
			yIDXara[i] = newIDX;
		}
		if (cntrMod > 2) {System.out.println("Error with cntrMod too large : " + cntrMod);}
		float modAmt;
		//calc new diffusion values by applying stencil to neighbors
		for (int i = widthStartIDX; i < calcWidth; ++i){
			if (xIDXara[i] < 0) continue;
			for (int j = heightStartIDX; j < calcHeight; ++j){
				if(yIDXara[j] < 0) continue;
				if (multAra[i][j] != 0){
					tmpCell = cellGrid.cellMap[xIDXara[i]][yIDXara[j]];
					tmpCalcU = tmpCell.conc[rs.chemU][0];//getConc(rs.chemU, 0);
					tmpCalcV = tmpCell.conc[rs.chemV][0];//getConc(rs.chemV, 0);
					modAmt = ((i == j) ?  cntrMod : 0);

					newDiffU += (tmpCalcU * (multAra[i][j] + modAmt )); 				
					newDiffV += (tmpCalcV * (multAra[i][j] + modAmt )); 	
				}
			}//for j - grid2D_Y			
		}//for i - grid2D_X	
				
		newDiffU *= (Ku * deltaT);
		newDiffV *= (Kv * deltaT);
		
		//cell.setConc(rs.chemU, 1, newDiffU);
		//cell.setConc(rs.chemV, 1, newDiffV);
		cellGrid.setConcAtCell(cell.x, cell.y, rs.chemU, updateIDX, newDiffU);
		cellGrid.setConcAtCell(cell.x, cell.y, rs.chemV, updateIDX, newDiffV);
	}//calcValueFENeumann
	
	
	public int getWidth(){return width;}
	public int getHeight(){return height;}
	public float[][] getMultAra(){return multAra;}
	//public void setTriDiag(boolean val){triDiag = val;}
	
	public String toString(){
		String result = "";
		result += "\nStencil grid2D_X : " +  width + " | grid2D_Y : " + height;
		result += "\nStencil : \n";
		for (int yVal = 0; yVal < height; ++yVal){
			result += ( (yVal == 0) ? ("{") : (""));
			for(int xVal = 0; xVal < width; ++xVal){
				result += ( (xVal == 0) ? ("{") : ("")) + "(" + xVal +"," + yVal  + ") = " + multAra[xVal][yVal] + ( (xVal == width -1) ? ("}") : (" | "));
			}//for each column val
			result +=  ( (yVal == height - 1) ? ("}\n") : ("\n"));
		
		}//for each row		
		return result;		
	}

}//class myStencil
