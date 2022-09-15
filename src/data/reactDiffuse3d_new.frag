#version 330
#ifdef GL_ES
precision mediump float;
#endif
#define PROCESSING_TEXTURE_SHADER
#define STENCIL_SIZE 27
uniform vec2 texOffset;
uniform sampler2D textureSmplr;

uniform float ru;         	// rate of diffusion of U
uniform float rv;          	// rate of diffusion of V
uniform float f;           	// f in grey scott
uniform float k;           	// k in grey scott
uniform float distZ;
uniform float deltaT;		// delta t  - shouldn't go higher than about 2 or blows up
uniform float diffOnly;		// 1 == diffusion only
uniform float locMap;		// 1 == location-based k and f map
in vec4 vertTexCoord;
out vec4 fragColor;

void main(void){
	float stencil3D[STENCIL_SIZE];
	vec2 offset3D[STENCIL_SIZE];	
	vec2 texCoord	=  vertTexCoord.st; // center coordinates
	float w	= texOffset.s;     // horizontal distance between texels - 1/texture width == 1/(width * depth)
	float h	= texOffset.t;     // vertical distance between texels 1/texture height
	float d	= distZ;			//depth dist - next layer is 1/widthX away

	float wd = (w+d);
	float wmd = (w-d);
	
	float stencilCtr = -3.0;//-2.5;
	float stencLvl1 = 0.166666;			//6 places
	float stencLvl2 = 0.083333;			//12 places
	float stencLvl3 = 0.1275;				//8 places

	//stencil multiplier for surrounding cube
	stencil3D[0]  = stencLvl3;	stencil3D[1]  = stencLvl2;	stencil3D[2]  = stencLvl3;
	stencil3D[3]  = stencLvl2;	stencil3D[4]  = stencLvl1;	stencil3D[5]  = stencLvl2;
	stencil3D[6]  = stencLvl3;	stencil3D[7]  = stencLvl2;	stencil3D[8]  = stencLvl3;
	
	stencil3D[9]  = stencLvl2;	stencil3D[10] = stencLvl1;	stencil3D[11] = stencLvl2;
	stencil3D[12] = stencLvl1;	stencil3D[13] = stencilCtr;	stencil3D[14] = stencLvl1;
	stencil3D[15] = stencLvl2;	stencil3D[16] = stencLvl1;	stencil3D[17] = stencLvl2;
	
	stencil3D[18] = stencLvl3;	stencil3D[19] = stencLvl2;	stencil3D[20] = stencLvl3;
	stencil3D[21] = stencLvl2;	stencil3D[22] = stencLvl1;	stencil3D[23] = stencLvl2;
	stencil3D[24] = stencLvl3;	stencil3D[25] = stencLvl2;	stencil3D[26] = stencLvl3;

	//stencil offset for surrounding cube
	//	-d : next lower plane
	offset3D[0] = vec2(-wd,-h);		offset3D[1] = vec2(-d,-h);		offset3D[2] = vec2(wmd,-h);
	offset3D[3] = vec2(-wd,0.0);	offset3D[4] = vec2(-d,0.0);		offset3D[5] = vec2(wmd,0.0);
	offset3D[6] = vec2(-wd,h);		offset3D[7] = vec2(-d,h);		offset3D[8] = vec2(wmd,h);
	//this plane
	offset3D[9] = vec2(-w,-h);		offset3D[10] = vec2(0.0, -h);	offset3D[11] = vec2(w, -h);
	offset3D[12] = vec2(-w,0.0);	offset3D[13] = vec2(0.0, 0.0);	offset3D[14] = vec2(w, 0.0);
	offset3D[15] = vec2(-w,h);		offset3D[16] = vec2(0.0, h);	offset3D[17] = vec2(w, h);
	//next higher plane +d
	offset3D[18] = vec2(-wmd,-h);	offset3D[19] = vec2(d, -h);		offset3D[20] = vec2(wd, -h);
	offset3D[21] = vec2(-wmd,0.0);	offset3D[22] = vec2(d, 0.0);	offset3D[23] = vec2(wd, 0.0);
	offset3D[24] = vec2(-wmd,h);	offset3D[25] = vec2(d, h);		offset3D[26] = vec2(wd, h);


	//vec2 UV = texture2D( texture, texCoord ).rg;
	vec2 UV = texture( textureSmplr, texCoord ).rg;
	float u	= UV.r;
	float v	= UV.g;

	vec2 lap = vec2( 0.0, 0.0);	
   // Loop through the neighboring pixels and compute Laplacian
	for( int i=0; i<STENCIL_SIZE; ++i ){
		//vec2 tmp	= texture2D( texture, texCoord + offset3D[i] ).rg;
		vec2 tmp	= texture( textureSmplr, texCoord + offset3D[i] ).rg;
		lap			+= tmp * stencil3D[i];
	}
	float F=f;
	float K=k;
	//for location-varying map - set to be coordinate based
	if(locMap==1.0){
		F = (texCoord.y) *.08;
		K = (texCoord.x *.04) + .03;
	} 
	float diffPartU = ru * lap.x;
	float diffPartV = rv * lap.y;
	
	if(diffOnly == 1.0){
		u += deltaT* diffPartU;	
		v += deltaT* diffPartV;		
	} else {
		float uvv	= u * v * v;
		u += deltaT*((F * (1.0 - u)) - uvv + diffPartU);	
		v += deltaT*((uvv - ((F + K) * v)) + diffPartV);		
	}
	fragColor = vec4( clamp( u, 0.0, 1.0 ), clamp( v, 0.0, 1.0 ), 0, 1.0 );
}
