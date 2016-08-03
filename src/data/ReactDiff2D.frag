#ifdef GL_ES
precision highp float;
#endif
#define PROCESSING_TEXTURE_SHADER
#define STENCIL_SIZE 9
uniform vec2 texOffset;
uniform sampler2D texture;  // U := r, V := g, other channels ignored

uniform float ru;         	// rate of diffusion of U
uniform float rv;          	// rate of diffusion of V
uniform float f;           	// f in grey scott
uniform float k;           	// k in grey scott
uniform float deltaT;		// delta t  - shouldn't go higher than 2 or blows up
uniform float diffOnly;		// 1 == diffusion only
uniform float locMap;		// 1 == location-based k and f map

varying vec4 vertTexCoord;

void main(void)
{
	float stencil2D[STENCIL_SIZE];
	vec2 offset[STENCIL_SIZE];
	vec2 texCoord	=  vertTexCoord.st; //vertex coord
	float w	= texOffset.s;     
	float h	= texOffset.t;    
	
	stencil2D[0] = .125;	stencil2D[1] = .25;	stencil2D[2] = .125;	
	stencil2D[3] = .25;		stencil2D[4] =-1.5;	stencil2D[5] = .25;	
	stencil2D[6] = .125;	stencil2D[7] = .25;	stencil2D[8] = .125;
	
	offset[0] = vec2( -w, -h);	offset[1] = vec2(0.0, -h);	offset[2] = vec2(  w, -h);
	offset[3] = vec2( -w, 0.0);	offset[4] = vec2(0.0, 0.0);	offset[5] = vec2(  w, 0.0);
	offset[6] = vec2( -w, h);	offset[7] = vec2(0.0, h);	offset[8] = vec2(  w, h);


	vec2 UV = texture2D( texture, texCoord ).rg;
	vec2 lap = vec2( 0.0, 0.0 );	
   // Loop through the neighbouring pixels and compute Laplacian

	for( int i=0; i<STENCIL_SIZE; i++ ){
		vec2 tmp	= texture2D( texture, texCoord + offset[i] ).rg;
		lap			+= tmp * stencil2D[i];
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

