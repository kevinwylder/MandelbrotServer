all:
	gcc mandelbrot.c -lpng && mv a.out ../m.out

parallel:
	nvcc parallelbrot.cu -lpng -lcudart -lcuda && mv a.out ../m.out

