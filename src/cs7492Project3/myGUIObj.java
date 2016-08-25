package cs7492Project3;

import java.util.ArrayList;

//object on menu that can be modified via mouse input
public class myGUIObj {
	public int ID;
	public cs7492Proj3 p;
	public myVector start, end;				//x,y coords of start corner, end corner (z==0) for clickable region
	public String name, dispText;

	public double val, minVal, maxVal;
	public boolean treatAsInt;
	public int _cVal;
	public double modMult;						//multiplier for mod value
	
	public int kfIdx;							//if k or f, then is 0, or 1, respectively, otherwise -1 - displays and modifies actual value in 
	
	public int[] bxclr;
	
	public myGUIObj(cs7492Proj3 _p,String _name, myVector _start, myVector _end, double _min, double _max, double _initVal, boolean _tAsInt, double _modMult) {
		p=_p;
		ID = p.GUIObjID++;
		name = _name;
		dispText = new String("UI Obj "+ID+" : "+name + " : ");
		start = new myVector(_start); end = new myVector(_end);
		minVal=_min; maxVal = _max; val = _initVal;
		treatAsInt = _tAsInt;
		_cVal = p.gui_Black;
		modMult = _modMult;
		bxclr = p.getRndClr();
		kfIdx = -1;
	}	
	public myGUIObj(cs7492Proj3 _p, String _name,float _xst, float _yst, float _xend, float _yend, double _min, double _max, double _initVal, boolean _tAsInt, double _modMult) {this(_p,_name,new myVector(_xst,_yst,0), new myVector(_xend,_yend,0), _min, _max, _initVal, _tAsInt, _modMult);	}
	
	public void setKFIdx(int _idx){kfIdx = _idx; val = p.curKFVals[kfIdx];}	
	public double getVal(){return (kfIdx == -1) ? val : p.curKFVals[kfIdx];}	
	public void setNewMax(double _newval){	maxVal = _newval;val = ((val >= minVal)&&(val<=maxVal)) ? val : (val < minVal) ? minVal : maxVal;		}
	public void setNewMin(double _newval){	minVal = _newval;val = ((val >= minVal)&&(val<=maxVal)) ? val : (val < minVal) ? minVal : maxVal;		}
	
	public double setVal(double _newVal){
		val = ((_newVal >= minVal)&&(_newVal<=maxVal)) ? _newVal : (_newVal < minVal) ? minVal : maxVal;		
		return val;
	}	
	
	public double modVal(double mod){
		if(kfIdx == -1){
			val += (mod*modMult);
			if(treatAsInt){val = Math.round(val);}		
			if(val<minVal){val = minVal;}
			else if(val>maxVal){val = maxVal;}
			return val;		
		} else {
			p.curKFVals[kfIdx] += (mod*modMult);
			if(treatAsInt){p.curKFVals[kfIdx] = Math.round(p.curKFVals[kfIdx]);}
			if(p.curKFVals[kfIdx]<minVal){p.curKFVals[kfIdx] = (float)minVal;}
			else if(p.curKFVals[kfIdx]>maxVal){p.curKFVals[kfIdx] =(float) maxVal;}
			val = p.curKFVals[kfIdx];
			return p.curKFVals[kfIdx];					
		}
	}
	
	public int valAsInt(){return (int)((kfIdx == -1) ? val : p.curKFVals[kfIdx]);}
	public float valAsFloat(){return (float)((kfIdx == -1) ? val : p.curKFVals[kfIdx]);}
	public boolean clickIn(float _clkx, float _clky){return (_clkx > start.x)&&(_clkx < end.x)&&(_clky > start.y)&&(_clky < end.y);}
	public void draw(){
		p.pushMatrix();p.pushStyle();
			p.translate((float)start.x, (float)(start.y + p.yOff));
			p.setColorValFill(_cVal);
			p.setColorValStroke(_cVal);
			p.pushMatrix();p.pushStyle();
				p.noStroke();
				p.fill(bxclr[0],bxclr[1],bxclr[2],bxclr[3]);
				p.translate((float)(-start.x * .5f),(float)( -p.yOff*.25f));
			p.box(5);
			p.popStyle();p.popMatrix();
			p.text(dispText + String.format("%.5f",val), 0,0);
		p.popStyle();p.popMatrix();
	}
	
	public void updateKF(){
		if(kfIdx != -1){p.curKFVals[kfIdx] = valAsFloat();}
	}
	
	public String[] getStrData(){
		ArrayList<String> tmpRes = new ArrayList<String>();
		tmpRes.add("ID : "+ ID+" Name : "+ name + " distText : " + dispText);
		tmpRes.add("Start loc : "+ start + " End loc : "+ end + " Treat as Int  : " + treatAsInt);
		tmpRes.add("Value : "+ val +" Max Val : "+ maxVal + " Min Val : " + minVal+ " Mod multiplier : " + modMult);
		
		
		return tmpRes.toArray(new String[0]);
	}
}
