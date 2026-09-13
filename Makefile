all: build

ifeq ($(OS),Windows_NT)
    RM = -cmd /c del /f /q
    FIX_PATH = $(subst /,\,$1)
else
    RM = -rm -f
    FIX_PATH = $1
endif

build-injector:
	@echo "Building injector classes (Forge + Fabric)"
	@javac --release 21 injector-src/ForgeInjector.java
	@javac --release 21 injector-src/FabricInjector.java

build-header-converter:
	@echo "Building class to header converter"
	@javac --release 21 header-converter-src/HeaderConverter.java

convert-forge-injector: build-injector build-header-converter
	@echo "Converting ForgeInjector class to .h"
	@java -cp header-converter-src HeaderConverter injector injector-src/ForgeInjector.class native-src/classes/injector.h

convert-fabric-injector: build-injector build-header-converter
	@echo "Converting FabricInjector class to .h"
	@java -cp header-converter-src HeaderConverter injector injector-src/FabricInjector.class native-src/classes/fabric_injector.h

convert-injector: convert-forge-injector convert-fabric-injector

convert-jar: build-header-converter
	@echo "Converting input jar to .h"
	@java -cp header-converter-src HeaderConverter input-jar input.jar native-src/classes/jar.h

native_files := main.cpp injector.cpp utils.cpp

build-library: convert-injector convert-jar
	@echo "Building output dll"
	@cd native-src && x86_64-w64-mingw32-g++ -O2 $(native_files) -shared -Wl,--dll -Wl,--exclude-all-symbols -static \
		 -static-libgcc -static-libstdc++ -luser32 -o ../output.dll

build: clean build-library
	@echo "Ready!"

clean-injector:
	@echo "Cleaning injector classes"
	@$(RM) $(call FIX_PATH,injector-src/*.class) >nul 2>&1 || exit 0

clean-header-converter:
	@echo "Cleaning class to header converter classes"
	@$(RM) $(call FIX_PATH,header-converter-src/*.class) >nul 2>&1 || exit 0

clean-headers:
	@echo "Cleaning converted injector and input jar"
	@$(RM) $(call FIX_PATH,native-src/classes/injector.h) >nul 2>&1 || exit 0
	@$(RM) $(call FIX_PATH,native-src/classes/fabric_injector.h) >nul 2>&1 || exit 0
	@$(RM) $(call FIX_PATH,native-src/classes/jar.h) >nul 2>&1 || exit 0

clean-output-library:
	@echo "Cleaning output dll"
	@$(RM) $(call FIX_PATH,output.dll) >nul 2>&1 || exit 0

clean: clean-injector clean-header-converter clean-headers clean-output-library
	@echo "Cleaning everything"
