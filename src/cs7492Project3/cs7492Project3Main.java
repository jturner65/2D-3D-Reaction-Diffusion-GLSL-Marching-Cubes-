package cs7492Project3;

import processing.core.PApplet;

public class cs7492Project3Main {

	public static void main(String[] passedArgs) {
	    String[] appletArgs = new String[] { "cs7492Project3.cs7492Proj3" };
	    if (passedArgs != null) {
	    	PApplet.main(PApplet.concat(appletArgs, passedArgs));
	    } else {
	    	PApplet.main(appletArgs);
	    }
	 }
}

