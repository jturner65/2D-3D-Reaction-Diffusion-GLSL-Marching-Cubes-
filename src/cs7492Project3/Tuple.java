package cs7492Project3;

//only works for primitives
public class Tuple<X,Y> implements Comparable<Tuple<X,Y>> { 
    public final X x;    public final Y y; 
    public Tuple(X x, Y y) {    this.x = x;   this.y = y;}
    public Tuple(Tuple<X,Y> _t) {   this( _t.x,_t.y);  }
    public String toString() {      return "(" + x + "," + y + ")";  }
    @Override
    public boolean equals(Object _o) {  if (_o == null) {return false;} if (_o == this) { return true; } if (!(_o instanceof Tuple)){ return false; } Tuple<X,Y> o = (Tuple<X,Y>) _o;  return o.x.equals(this.x) && o.y.equals(this.y);  }
    public int hashCode() { int result = 97 + ((x == null) ? 0 : x.hashCode()); return 97 * result + ((y == null) ? 0 : y.hashCode()); }
	public Float getSqMag(){if((x != null) && (y != null)) { return 1.0f*((x.hashCode()*x.hashCode()) + (y.hashCode()*y.hashCode()));} else {return null;}}
 	@Override
	public int compareTo(Tuple<X, Y> arg0) {//not a good measure - need to first use dist
 		return (this.hashCode() > arg0.hashCode() ? 1 : (this.hashCode() < arg0.hashCode() ? -1 : (this.equals(arg0) ? 0 : 1)));
	}
}

//only works for primitives
class Triple<X,Y,Z> implements Comparable<Triple<X,Y,Z>> { 
	public final X x;    public final Y y; public final Z z;
	public Triple(X x, Y y, Z z) {    this.x = x;   this.y = y; this.z = z;}
	public Triple(Triple<X,Y,Z> _t) {    this( _t.x,_t.y, _t.z);  }
	public String toString() {      return "(" + x + "," + y +"," + z + ")";  }
	@Override
	public boolean equals(Object _o) {  if (_o == null) {return false;} if (_o == this) { return true; } if (!(_o instanceof Triple)){ return false; } Triple<X,Y,Z> o = (Triple<X,Y,Z>) _o;  return o.x.equals(this.x) && o.y.equals(this.y)&& o.z.equals(this.z);  }
	public int hashCode() { int result = 97 + ((x == null) ? 0 : x.hashCode()); result = 97 * result + ((y == null) ? 0 : y.hashCode()); return 97 * result + ((z == null) ? 0 : z.hashCode()); }
	@Override
	public int compareTo(Triple<X,Y,Z> arg0) {
		return (this.hashCode() > arg0.hashCode() ? 1 : (this.hashCode() < arg0.hashCode() ? -1 : (this.equals(arg0) ? 0 : 1)));
	}
}//Triple<X,Y,Z>