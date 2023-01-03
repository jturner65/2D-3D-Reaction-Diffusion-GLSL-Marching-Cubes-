package cs7492Project3;

/**
 * class to store vertexes of triangle - will calculate normal based on all triangles that share this vertex
 * @author John Turner
 *
 */
public class myMCVert extends myPointf{
	public myVectorf n = new myVectorf();
	public myPointf clr = new myVectorf();
	public int numLocs = 0;
	
	public myMCVert(){super();_setClrFromLoc();}
	public myMCVert(myPointf A, float s, myPointf B) {super(A,s,B); _setClrFromLoc();}
	
	/**
	 * Add a location value to this vertex. The sum of all vert values will be averaged by numLocs.
	 * @param _loc
	 */
	public void addVertLoc(myPointf _loc){
		++numLocs;
		_add(_loc);		
	}
	
	/**
	 * Add an unnormalized face normal from another poly that shares this vertex.
	 * @param _norm unnormalized outward-facing normal
	 */
	public void addToNorm(myVectorf _norm){		n._add(_norm);	}
	
	/**
	 * Use average location to try to smooth interpolation issues between settings - need to do before we calc the norm!
	 */
	public void calcAvgLoc() {
		if(numLocs > 1) {			_div(numLocs);		}
		_setClrFromLoc();
	}
	
	/**
	 * after all verts have been assigned, finalize processing (normalize norm and take average of vert locations
	 */
	public void normalize() {		n._normalize();	}
	
	/**
	 * Set the color for this vertex based on the location values
	 */
	private void _setClrFromLoc() {	clr.set(myPointf._mult(this, .5f));}	
	
}//class myMCVert

