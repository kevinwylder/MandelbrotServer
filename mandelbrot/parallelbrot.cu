#include <stdint.h>
#include <unistd.h>

#include <png.h>
#include <cuda.h>
#include <math.h>

#define rel params[0]
#define img params[1]
#define scl params[2]

__device__ void writeHSV(uint8_t *pixel, int theta) {
    unsigned char region, remainder, q, t;
    region = theta / 43;
    remainder = (theta - (region * 43)) * 6;
   
    q = (255 * (255 - ((255 * remainder) >> 8))) >> 8;
    t = (255 * (255 - ((255 * (255 - remainder)) >> 8))) >> 8;

    switch (region) {
        case 0:
            *pixel++ = 255;
            *pixel++ = t;
            *pixel++ = 0;
            return;
        case 1:
            *pixel++ = q;
            *pixel++ = 255;
            *pixel++ = 0;
            return;
        case 2:
            *pixel++ = 0;
            *pixel++ = 255;
            *pixel++ = t;
            return;
        case 3:
            *pixel++ = 0;
            *pixel++ = q;
            *pixel++ = 255;
            return;
        case 4:
            *pixel++ = t;
            *pixel++ = 0;
            *pixel++ = 255;
            return;
        default:
            *pixel++ = 255;
            *pixel++ = 0;
            *pixel++ = q;
            return;
    }
}

__global__ void euclid (uint8_t *gpu, double *params, int streamNumber ) {
    int index, pos;
    int c, t;
    uint32_t x, y; 

    index = streamNumber * 65536 + threadIdx.x * 256;
    for (pos = 0; pos < 256; pos++) {
        x = (uint32_t) (((rel + 2.0) + (double) (.5 + (index % 1024)) * scl) * 1048576);
        y = (uint32_t) (((img + 2.0) + (double) (.5 + (index / 1024)) * scl) * 1048576);
        c = 0;
        t = 1;
        while (1) {
            if (x > y) {
                x -= y;
                c++;
            } else if (y > x) {
                y -= x;
            } else {
                break;
            }
            t++;
            if (t > 1000) break;
        }

        uint8_t *pixel = (gpu + index++ * 3);
        *pixel++ = (255 * c) / t;
        *pixel++ = (255 * c) / t;
        *pixel++ = (255 * c) / t;
    }
}

__global__ void mandelbrot (uint8_t *gpu, double *params, int streamNumber ) {
    int index, c, pos;
    double cr, ci, zr, zi, t;

    index = streamNumber * 65536 + threadIdx.x * 256;
    for (pos = 0; pos < 256; pos++) {
        c = 0;
        cr = rel + (double) (.5 + (index % 1024)) * scl / 1024.0;
        ci = img + (double) (.5 + (index / 1024)) * scl / 1024.0;
        zr = cr;
        zi = ci;

        while (++c < 1000 && zr * zr + zi * zi < 4) {
            t = zr;
            zr = zr * zr - zi * zi + cr;
            zi = 2 * t * zi + ci;
        }
    
        uint8_t *pixel = (gpu + index * 3);
        if (c == 1000) {
            *pixel++ = 0;
            *pixel++ = 0;
            *pixel++ = 0;
        } else {
            writeHSV(pixel, c);
        }
        index ++;
    }
}

// GPU variables
double *gpu_params;
uint8_t *gpu; 

// Host variables
cudaStream_t streams[16];
double params[3];
png_byte ** row_pointers;
void (*kernel) (uint8_t *, double *, int);


// reads parameters from stdin and writes them to params array
// initializes rel, img, and scl macros
void readParams() {
    rel = -2.0;
    img = -2.0;
    scl = 4.0;
    char c = getchar();
    switch (c) {
        case 'm':
            kernel = mandelbrot;
            break;
        default:
            kernel = euclid;
    }
    while ((c = getchar()) != '@') {
        scl /= 3.0;
        switch (c) {
            case '3':
            case '6':
            case '9':
                rel += scl;
            case '2':
            case '5':
            case '8':
                rel += scl;
            default:
                break;
        }
        switch (c) {
            case '7':
            case '8':
            case '9':
                img += scl;
            case '4':
            case '5':
            case '6':
                img += scl;
            default:
                break;
        }
    }
}

// begins computation
void computeKernel() {
    // setup params
    cudaMemcpy( gpu_params, params, 3 * sizeof(double), cudaMemcpyHostToDevice);

    // initialize streams
    int i, r;
    for (i = 0; i < 16; i++) {
        cudaStreamCreate((streams + i));
    }

    // execute kernels in the streams
    for (i = 0; i < 16; i++) {
        kernel<<<1, 256, 0, streams[i]>>>( gpu, gpu_params, i );
    }

    // setup asynchronous memory copy after completion
    for (i = 0; i < 16; i++) {
        for (r = 0; r < 64; r++) {
            cudaMemcpyAsync(row_pointers[64 * i + r], (gpu + i * 65536 * 3 + r * 1024 * 3), sizeof(uint8_t) * 1024 * 3, cudaMemcpyDeviceToHost, streams[i]);
        }
    }

    cudaDeviceSynchronize();
}

extern void writePngOutput();

int main(int argc, char **argv) {

    // Initialize memory
    cudaMalloc( (void**)  &gpu, 1024 * 1024 * sizeof(uint8_t) * 3 );
    cudaMalloc( (void**)  &gpu_params, 3 * sizeof(double) );

    row_pointers = (png_byte **) malloc (1024 * sizeof (png_byte *));
    for (int y = 0; y < 1024; y++) {
        row_pointers[y] = (png_byte *) malloc (sizeof (uint8_t) * 1024 * 3);
    }

   
    // do the process
    while (1) {
        readParams();
        computeKernel();
        writePngOutput();
    }

}

size_t pngBufferFill = 0;
extern void writeFn(png_structp png_ptr, png_bytep data, uint32_t size);
extern void flushFn(png_structp png_ptr);

void writePngOutput() {

    png_structp png_ptr = png_create_write_struct (PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    png_infop   info_ptr = png_create_info_struct (png_ptr);
    
    png_set_IHDR (png_ptr,
            info_ptr,
            1024,                           // width
            1024,                           // height
            8,                              // depth
            PNG_COLOR_TYPE_RGB,
            PNG_INTERLACE_NONE,
            PNG_COMPRESSION_TYPE_DEFAULT,
            PNG_FILTER_TYPE_DEFAULT);

    png_set_write_fn(png_ptr, NULL, (png_rw_ptr) writeFn, (png_flush_ptr) flushFn);

    png_init_io (png_ptr, stdout);
    png_set_rows (png_ptr, info_ptr, row_pointers);
    png_write_png (png_ptr, info_ptr, PNG_TRANSFORM_IDENTITY, NULL);

    write(2, &pngBufferFill, 4);
    pngBufferFill = 0;

    png_destroy_write_struct (&png_ptr, &info_ptr);
}

void writeFn(png_structp png_ptr, png_bytep data, uint32_t size) {
    write(1, data, size);
    pngBufferFill += size;
}

void flushFn(png_structp png_ptr) {
    fflush(stdout);
}
