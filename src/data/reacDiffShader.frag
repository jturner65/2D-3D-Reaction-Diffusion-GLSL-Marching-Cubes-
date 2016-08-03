#ifdef GL_ES
precision mediump float;
#endif
#define PROCESSING_TEXTURE_SHADER
#define STENCIL3D_SIZE 27
uniform sampler2D texture; 
uniform vec2 texOffset;
//shader expects each page of z/d info to be an extension in the x/w direction if 3d.
//so a 3d result of 100x100x100 should be sent as a texture of 10,000 wide by 100 high pixels, and a dOffset of .01

uniform float ru;         	// rate of diffusion of U
uniform float rv;          	// rate of diffusion of V
uniform float f;           	// f in grey scott
uniform float k;           	// k in grey scott
uniform int stencilSize;		//9 if 2D, 27 if 3D
uniform int numIters;
uniform float deltaT;		// delta t  - shouldn't go higher than 2 or blows up
uniform float diffOnly;		// 1 == diffusion only
uniform float locMap;		// 1 == location-based k and f map
uniform float dOffset;		// 1/how large page is -> offset for each page of d/z dimension in width

varying vec4 vertTexCoord;

float[STENCIL3D_SIZE] defStencil(bool check2d){
	float[STENCIL3D_SIZE] ret;
	if(check2d){
		ret = float[STENCIL3D_SIZE](
		0.125, 0.25,0.125,	
		0.25,  2.0, 0.25,	
		0.125, 0.25,0.125,
		0,0,0,0,0,0,
		0,0,0,0,0,0,
		0,0,0,0,0,0);	
	} else {
		ret = float[STENCIL3D_SIZE](
		0.0,	0.02778, 0.0,
		0.02778,0.11111, 0.02778,
		0.0,	0.02778, 0.0,		
		0.02778,0.11111, 0.02778,
		0.11111,-1.0, 0.11111,
		0.02778,0.11111, 0.02778,		
		0.0,	0.02778, 0.0,
		0.02778,0.11111, 0.02778,
		0.0,	0.02778, 0.0
		);
	}
	return ret;
}	

vec2[STENCIL3D_SIZE] defOffset(bool check2d, int w, int h, int wd, int wmd){
	vec2[STENCIL3D_SIZE] ret;
	if(check2d){
		ret =vec2[STENCIL3D_SIZE](
		vec2( -w, -h),	vec2(0.0, -h), 	vec2(  w, -h),	
		vec2( -w, 0.0),	vec2(0.0, 0.0),	vec2(  w, 0.0),
		vec2( -w, h),	vec2(0.0, h),	vec2(  w, h),		
		vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),
		vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),
		vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0),vec2(0.0, 0.0)
		);	
	} else {
		ret = vec2[STENCIL3D_SIZE](
		vec2(-wd,-h), 	vec2(-d,-h),	vec2(wmd,-h),	
		vec2(-wd,0.0),	vec2(-d,0.0),	vec2(wmd,0.0),	
		vec2(-wd,h),	vec2(-d,h),		vec2(wmd,h),		
		vec2(-w,-h),	vec2(0.0, -h),	vec2(  w, -h),	
		vec2(-w,0.0),	vec2(0.0, 0.0),	vec2(w, 0.0),	
		vec2(-w,h),		vec2(0.0, h),	vec2(  w, h),						 
		vec2(-wmd,-h),	vec2(d, -h),	vec2(wd, -h),	
		vec2(-wmd,0.0),	vec2(d, 0.0),	vec2(  wd, 0.0),	
		vec2(-wmd,h),	vec2(d, h),		vec2(  wd, h)
        );
	}
	return ret;
}	

void main(void){	
	vec2 texCoord	=  vertTexCoord.st; // center coordinates
	float w	= texOffset.s;     // horizontal distance between texels - 1/10th
	float h	= texOffset.t;     // vertical distance between texels
	float wd = (w+dOffset);//mod(10*(w+d),10.0)/10.0;
	float wmd = (w-dOffset);//mod(10*(w-d+1),10)/10.0;

	float stencil[STENCIL3D_SIZE] = defStencil(stencilSize==9);
	vec2 offset[STENCIL3D_SIZE] = defStencil(stencilSize==9, w, h, wd, wmd);	
	
	vec2 UV = texture2D( texture, texCoord ).rg;
	vec2 lap = vec2( 0.0, 0.0);	
   // Loop through the neighbouring pixels and compute Laplacian

	for( int i=0; i<stencilSize; i++ ){
		vec2 tmp	= texture2D( texture, texCoord + offset[i] ).rg;
		lap			+= tmp * stencil[i];
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
	float u	= UV.r;
	float v	= UV.g;
	
	if(diffOnly == 1.0){
		u += deltaT* diffPartU;	
		v += deltaT* diffPartV;		
	} else {
		float uvv	= u * v * v;
		u += deltaT*((F * (1.0 - u)) - uvv + diffPartU);	
		v += deltaT*((uvv - ((F + K) * v)) + diffPartV);		
	}
	gl_FragColor = vec4( clamp( u, 0.0, 1.0 ), clamp( v, 0.0, 1.0 ), 0, 1.0 );
}
