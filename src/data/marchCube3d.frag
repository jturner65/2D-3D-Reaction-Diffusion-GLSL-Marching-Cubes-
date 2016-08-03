#ifdef GL_ES
precision highp float;
#endif
#define PROCESSING_TEXTURE_SHADER

uniform vec2 texOffset;
uniform sampler2D texture;  

uniform float distZ;
uniform float dispChemU;	//whether we are displaying chem U or V
uniform float isoLvl;		// isolevel for chem
uniform float gridPxlX;		//how big the resultant grid is in the x direction
uniform float gridPxlY;		//y dir
uniform float gridPxlZ;		//z dir
uniform float cellSize;		//resultant cubes are this size on a side



varying vec4 vertTexCoord;

void main(void)
{
	vec2 texCoord	=  vertTexCoord.st; // center coordinates
	float w	= texOffset.s;     // horizontal distance between texels 
	float h	= texOffset.t;     // vertical distance between texels
	float d	= distZ;			//depth dist - next layer is distZ - some ratio of the entire width away
	float wd = (w+d);			
	float wmd = (w-d);			

	vec2 offset3D[8];		//4 corners of cube
	offset3D[0] = vec2(0,0);	offset3D[1] = vec2(w,0); 	offset3D[2] = vec2(w,h);	offset3D[3] = vec2(0.0,h);	
	offset3D[4] = vec2(d,0.0);	offset3D[5] = vec2(wd,0.0);	offset3D[6] = vec2(wd,h);	offset3D[7] = vec2(d,h);
	
	vec3 pos3D[8];
	
	vec2 vIdx[12];
	vIdx[0] = vec2(0,1);vIdx[1] = vec2(1,2);vIdx[2] = vec2(2,3);vIdx[3] = vec2(3,0);
	vIdx[4] = vec2(4,5);vIdx[5] = vec2(5,6);vIdx[6] = vec2(6,7);vIdx[7] = vec2(7,4);
	vIdx[8] = vec2(0,4);vIdx[9] = vec2(1,5);vIdx[10] = vec2(2,6);vIdx[11] = vec2(3,7);

	int pow2[16] = int[16](1,2,4,8,16,32,64,128,256,512,1024,2048,4096,8192,16384,32768);
	float concVal[8];
	int cubeIDX = 0;
	int clrIdx = (int)(1 - dispChemU);
	for( int i=0; i<8; i++ ){
		concVal[i]	= texture2D( texture,(texCoord + offset3D[i]))[clrIdx];
		if(concVal[i] < isoLvl){	cubeIDX |= pow2[i];}
	}
	
	float cubeIDXRes = cubeIDX/256.0, edgTblRes = 0;
	if((cubeIDX != 0) && (cubeIDX != 255)){
		int edgeTable[256] = int[256](
		0x0,   0x109, 0x203, 0x30a, 0x406, 0x50f, 0x605, 0x70c, 0x80c, 0x905, 0xa0f, 0xb06, 0xc0a, 0xd03, 0xe09, 0xf00, 
		0x190, 0x99,  0x393, 0x29a, 0x596, 0x49f, 0x795, 0x69c, 0x99c, 0x895, 0xb9f, 0xa96, 0xd9a, 0xc93, 0xf99, 0xe90, 
		0x230, 0x339, 0x33,  0x13a, 0x636, 0x73f, 0x435, 0x53c, 0xa3c, 0xb35, 0x83f, 0x936, 0xe3a, 0xf33, 0xc39, 0xd30, 
		0x3a0, 0x2a9, 0x1a3, 0xaa,  0x7a6, 0x6af, 0x5a5, 0x4ac, 0xbac, 0xaa5, 0x9af, 0x8a6, 0xfaa, 0xea3, 0xda9, 0xca0, 
		0x460, 0x569, 0x663, 0x76a, 0x66,  0x16f, 0x265, 0x36c, 0xc6c, 0xd65, 0xe6f, 0xf66, 0x86a, 0x963, 0xa69, 0xb60, 
		0x5f0, 0x4f9, 0x7f3, 0x6fa, 0x1f6, 0xff,  0x3f5, 0x2fc, 0xdfc, 0xcf5, 0xfff, 0xef6, 0x9fa, 0x8f3, 0xbf9, 0xaf0, 
		0x650, 0x759, 0x453, 0x55a, 0x256, 0x35f, 0x55,  0x15c, 0xe5c, 0xf55, 0xc5f, 0xd56, 0xa5a, 0xb53, 0x859, 0x950, 
		0x7c0, 0x6c9, 0x5c3, 0x4ca, 0x3c6, 0x2cf, 0x1c5, 0xcc,  0xfcc, 0xec5, 0xdcf, 0xcc6, 0xbca, 0xac3, 0x9c9, 0x8c0,
		0x8c0, 0x9c9, 0xac3, 0xbca, 0xcc6, 0xdcf, 0xec5, 0xfcc, 0xcc,  0x1c5, 0x2cf, 0x3c6, 0x4ca, 0x5c3, 0x6c9, 0x7c0, 
		0x950, 0x859, 0xb53, 0xa5a, 0xd56, 0xc5f, 0xf55, 0xe5c, 0x15c, 0x55,  0x35f, 0x256, 0x55a, 0x453, 0x759, 0x650, 
		0xaf0, 0xbf9, 0x8f3, 0x9fa, 0xef6, 0xfff, 0xcf5, 0xdfc, 0x2fc, 0x3f5, 0xff,  0x1f6, 0x6fa, 0x7f3, 0x4f9, 0x5f0, 
		0xb60, 0xa69, 0x963, 0x86a, 0xf66, 0xe6f, 0xd65, 0xc6c, 0x36c, 0x265, 0x16f, 0x66,  0x76a, 0x663, 0x569, 0x460, 
		0xca0, 0xda9, 0xea3, 0xfaa, 0x8a6, 0x9af, 0xaa5, 0xbac, 0x4ac, 0x5a5, 0x6af, 0x7a6, 0xaa,  0x1a3, 0x2a9, 0x3a0, 
		0xd30, 0xc39, 0xf33, 0xe3a, 0x936, 0x83f, 0xb35, 0xa3c, 0x53c, 0x435, 0x73f, 0x636, 0x13a, 0x33,  0x339, 0x230, 
		0xe90, 0xf99, 0xc93, 0xd9a, 0xa96, 0xb9f, 0x895, 0x99c, 0x69c, 0x795, 0x49f, 0x596, 0x29a, 0x393, 0x99,  0x190, 
		0xf00, 0xe09, 0xd03, 0xc0a, 0xb06, 0xa0f, 0x905, 0x80c, 0x70c, 0x605, 0x50f, 0x406, 0x30a, 0x203, 0x109, 0x0 );
		vec2 vertList[12] = vec2[12]((0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0),(0,0));
		
		float t;
		// Find the vertices where the surface intersects the cube
		for(int i =0; i<vIdx.length;++i){
			if ((edgeTable[cubeIDX] & pow2[i]) != 0){
				if((abs(isoLvl - concVal[vIdx[i][0]]) < .00001) || (abs(concVal[vIdx[i][0]] - concVal[vIdx[i][1]]) < isoDel)) {
					pos3D[vIdx[i][0]];
				} else if (abs(isoLvl - concVal[vIdx[i][1]]) < isoDel){
					pos3D[vIdx[i][1]] :
				} else {
					t = (isoLvl - concVal[vIdx[i][0]])(concVal[vIdx[i][1]] - concVal[vIdx[i][0]]);
					mix(pos3D[vIdx[i][0]], pos3D[vIdx[i][1]], t);
				}
			}
		}
		// Create the triangle
		int numTri = 0;
		for (int i = 0; triTable[cubeIDX][i] != -1; i += 3) {
			lclTris[numTri].p[0] = vertList[triTable[cubeIDX][i]];
			lclTris[numTri].p[1] = vertList[triTable[cubeIDX][i + 1]];
			lclTris[numTri].p[2] = vertList[triTable[cubeIDX][i + 2]];
//			triVerts[numTriVertVals++] = lclTris[numTri].p[0].x;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[0].y;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[0].z;			
//			triVerts[numTriVertVals++] = 1;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[1].x;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[1].y;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[1].z;
//			triVerts[numTriVertVals++] = 1;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[2].x;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[2].y;
//			triVerts[numTriVertVals++] = lclTris[numTri].p[2].z;
//			triVerts[numTriVertVals++] = 1;			
			numTri++;
		}





		edgTblRes = edgeTable[cubeIDX] / 32768;
	}
	
	
	//gl_FragData[]
	//gl_FragColor = vec4(clamp(cubeIDXRes,0.0,1.0),clamp(edgTblRes,0.0,1.0),0.0,1.0);

}

