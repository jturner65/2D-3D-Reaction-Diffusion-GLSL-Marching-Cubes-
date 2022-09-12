package cs7492Project3;

public class myCellGrid {
	public myRDSolver rs;
	public int gridWidth;
	public int gridHeight;
	public int gridDepth;
	
	public myCell[][] cellMap;
	
	//concentration at idx0 maps for ADI calculation
	private float[][][] concMapX;
	private float[][][] concMapY;
	public int numVals;
	// amount to offset x coord of cell to use as key for hashmap - key is x *
	// keyModVal + y
	public final int keyModVal = 1000;
	
	public myCellGrid(cs7492Proj3 _p, myRDSolver _rs, int _width, int _height){
		rs = _rs;
		gridWidth = _width;
		gridHeight = _height;
		gridDepth = 0;				//TODO 3d stuff
		float[] randVal = new float[rs.seedNum];
		for (int i = 0; i < rs.seedNum; ++i) {randVal[i] = _p.random(gridWidth);}		
		cellMap = new myCell[gridHeight][gridWidth];
		//cellMap = new HashMap<Integer, myCell>();
		//holds idx 0 of concentration for both chemicals, idx'ed by chem type, x location, y location
		concMapX = new float[2][gridHeight][gridWidth];
		concMapY = new float[2][gridWidth][gridHeight];
		for (int cellX = 0; cellX < gridWidth; ++cellX) {
			for (int cellY = 0; cellY < gridHeight; ++cellY) {
				myCell tmpCell = new myCell(_p, rs, cellX, cellY);
				if(rs.checkRandAssign(cellX, cellY, randVal)){
					tmpCell.setConc(rs.chemV, 0, .25f);// * random(.9f,1.1f)); //v
					tmpCell.setConc(rs.chemU, 0, .5f);// * random(.9f, 1.1f)); //u
				}
				addCell(cellX, cellY, tmpCell);
			}// for each y cell location
		}// for each x cell location		
		numVals = gridWidth * gridHeight;
	}//myCellGrid constructor

	public void addCell (int x, int y, myCell cell){
		//Integer key = getKeyFromIntCoords(x,y);
		//cellMap.put(key, cell);
		cellMap[x][y] = cell;
		concMapX[rs.chemU][y][x] = cell.conc[rs.chemU][0];//cell.getConc(rs.chemU, 0);
		concMapX[rs.chemV][y][x] = cell.conc[rs.chemV][0];//cell.getConc(rs.chemV, 0);
		concMapY[rs.chemU][x][y] = cell.conc[rs.chemU][0];//cell.getConc(rs.chemU, 0);
		concMapY[rs.chemV][x][y] = cell.conc[rs.chemV][0];//cell.getConc(rs.chemV, 0);
	}//addcell

	/**
	 * set cell concentration[concIDX] of chem typeIDX at cell location x,y to be val 
	 * @param x x location of cell
	 * @param y y location of cell
	 * @param typeIDX type of chemical
	 * @param concIDX index into concentration array to set
	 * @param val value to set concentration to
	 */
	public void setConcAtCell(int x, int y, int typeIDX, int concIDX, float val){
	//	Integer key = getKeyFromIntCoords(x,y);
		cellMap[x][y].setConc(typeIDX, concIDX, val);
		if (concIDX == 0){//only set concentration map to be value if setting actual value of cell's base concentration
			concMapX[typeIDX][y][x] = val;
			concMapY[typeIDX][x][y] = val;
		}
	}//setConcAtCell
	
	/**
	 * updates concentration at a cell in idx 0 with final calculation value
	 * @param x the x coord of the cell
	 * @param y the y coord of the cell
	 * @param concIDX the index into the cell's concentration array for chemical typeIDX to be updated to idx 0
	 */
	public void updateConcAtCell(int x, int y, int concIDX){
		//Integer key = getKeyFromIntCoords(x,y);
		cellMap[x][y].updateConcentration(concIDX);
		for (int typeIDX = 0; typeIDX < 2; ++typeIDX){
			concMapX[typeIDX][y][x] = cellMap[x][y].conc[typeIDX][0];
			concMapY[typeIDX][x][y] = cellMap[x][y].conc[typeIDX][0];//getConc(typeIDX, 0);
		}
	}

	/**
	 * updates concentration at a cell in idx 0 with final calculation value for ADI calculation
	 * @param x the x coord of the cell
	 * @param y the y coord of the cell
	 * @param concIDX the index into the cell's concentration array for chemical typeIDX to be updated to idx 0
	 */
	public void updateConcAtCellIMP(int x, int y, int concIDX){
		cellMap[x][y].updateConcentrationIMP(concIDX);
		for (int typeIDX = 0; typeIDX < 2; ++typeIDX){
			concMapX[typeIDX][y][x] = cellMap[x][y].conc[typeIDX][0];//getConc(typeIDX, 0);
			concMapY[typeIDX][x][y] = cellMap[x][y].conc[typeIDX][0];//getConc(typeIDX, 0);
		}
	}
	
	public float[][] getConcMapX(int idx){return concMapX[idx];}
	public float[][] getConcMapY(int idx){return concMapY[idx];}

	
}//myCellGrid class
