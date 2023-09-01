proto_path = /home/cheewah.seetoh/Downloads/protobuf-3.12.1/src/

all: build-proto build-main run

build-proto: reference.proto
	$(proto_path)protoc --cpp_out=./generated-cpp -I./ reference.proto 

build-main: generated-cpp/reference.pb.h generated-cpp/reference.pb.cc main.cc
	g++ -w -o main -I $(proto_path) \
    ./generated-cpp/reference.pb.cc \
	main.cc \
	$(proto_path).libs/libprotobuf.a

run: main
	./main
