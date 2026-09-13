all: build

build-header-converter:
	@echo "Building class to header converter"
	@javac --release 21 header-converter-src/HeaderConverter.java

convert-jar: build-header-converter
	@echo "Converting input jar to raw bytes header"
	@java -cp header-converter-src HeaderConverter input.jar native-src/classes/jar.h

native_files := main.cpp

build-library: convert-jar
	@echo "Building output dll"
	@cd native-src && x86_64-w64-mingw32-g++ -O2 $(native_files) -shared -Wl,--dll -static \
		 -static-libgcc -static-libstdc++ -o ../output.dll

build: clean build-library
	@echo "Ready!"

clean-header-converter:
	@echo "Cleaning converter classes"
	@rm -f header-converter-src/*.class

clean-headers:
	@echo "Cleaning converted jar header"
	@rm -f native-src/classes/jar.h

clean-output-library:
	@echo "Cleaning output dll"
	@rm -f output.dll

clean: clean-header-converter clean-headers clean-output-library
	@echo "Cleaning everything"
