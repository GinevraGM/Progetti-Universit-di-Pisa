SHELL = /bin/bash

CC = gcc

CFLAGS = -std=c99 -Wall -D_POSIX_C_SOURCE -D_GNU_SOURCE -pedantic -I ./includes/ 

EXE = farm 

OBJS = objs/main.o objs/Master_thread.o objs/boundedqueue.o objs/Worker_consumer.o objs/Collector.o objs/lista_ordinata.o

LIBS = -lpthread

TARGETS=farm\
	generafile

.PHONY : test clean cleanall

$(EXE): $(OBJS)
	$(CC) $(CFLAGS) $^ -o $(EXE)
		
generafile: generafile.o
	$(CC) $(CFLAGS) generafile.o -o generafile
	
generafile.o: generafile.c
	$(CC) generafile.c -c generafile.c

objs/boundedqueue.o : src/boundedqueue.c includes/util.h includes/boundedqueue.h
	$(CC) $(CFLAGS) -c $< -o objs/boundedqueue.o $(LIBS)

objs/Master_thread.o : src/Master_Thread.c includes/boundedqueue.h includes/Worker_consumer.h 
	$(CC) $(CFLAGS) -c $< -o objs/Master_thread.o 

objs/Collector.o : src/Collector.c 
	$(CC) $(CFLAGS) -c $< -o objs/Collector.o

objs/Worker_consumer.o : src/Worker_consumer.c includes/boundedqueue.h 
	$(CC) $(CFLAGS) -c $< -o objs/Worker_consumer.o 

objs/lista_ordinata.o : src/lista_ordinata.c includes/lista_ordinata.h 
	$(CC) $(CFLAGS) -c $< -o objs/lista_ordinata.o
	
objs/main.o : src/main.c includes/Master_thread.h includes/Collector.h 
	$(CC) $(CFLAGS) -c $< -o objs/main.o
	
test: farm
	@chmod +x ./test.sh
	./test.sh

clean :
	-rm -f $(TARGETS) $(OBJS) generafile.o $(EXE) *~ ./farm.sck core

cleanall :
	-rm -f $(TARGETS) $(OBJS) generafile.o $(EXE) *~ *.dat ./farm.sck expected.txt core


