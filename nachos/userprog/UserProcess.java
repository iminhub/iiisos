package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	boolean intStatus = Machine.interrupt().disable();
    	this.parent = UserKernel.currentProcess();
    	this.processID = process++;
    	files = new FileDescriptor[16];
    	children = new LinkedList<Child>();
    	stdLock = new Lock();
    	if(parent == null) {
    		files[0] = new FileDescriptor(null, UserKernel.console.openForReading());
    		files[1] = new FileDescriptor(null, UserKernel.console.openForWriting());
    	} else {
    		files[0] = parent.files[0];
    		files[1] = parent.files[1];
    	}
    	Machine.interrupt().restore(intStatus);
    	/*
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	*/
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	thread = new UThread(this);
	thread.setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;
	if(vaddr >= pageTable.length * pageSize)
		return 0;
	int leng = Math.min(length, pageTable.length* pageSize - vaddr);
	int amount = 0;
	int spage = vaddr / pageSize;
	int epage = (vaddr + leng - 1) / pageSize;
	for(int start = spage; start < epage; start++) {
		if(!pageTable[start].valid)
			return amount;
		if(start == spage) {
			amount += pageSize - vaddr + spage * pageSize;
			System.arraycopy(memory, pageTable[spage].ppn * pageSize + vaddr - spage * pageSize, data, offset, amount);
			pageTable[spage].used = true;
		} else {
			System.arraycopy(memory, pageTable[start].ppn * pageSize, data, offset + amount, pageSize);
			amount += pageSize;
			pageTable[start].used = true;
		}
		
	}
	if(!pageTable[epage].valid)
		return amount;
	if(spage < epage) {
		System.arraycopy(memory, pageTable[epage].ppn * pageSize, data, offset + amount, (leng + vaddr) % pageSize);
		amount += (leng + vaddr) % pageSize;
	} else if(spage == epage) {
		System.arraycopy(memory, pageTable[epage].ppn * pageSize + vaddr % pageSize, data, offset + amount, leng);
		amount += leng;
	}
	pageTable[epage].used = true;
	//amount += (leng + vaddr) % pageSize;
	/*
	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);
	*/
	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;
	if(vaddr >= pageTable.length * pageSize)
		return 0;
	int leng = Math.min(length, pageTable.length* pageSize - vaddr);
	int amount = 0;
	int spage = vaddr / pageSize;
	int epage = (vaddr + leng - 1) / pageSize;
	for(int start = spage; start < epage; start++) {
		if(!pageTable[start].valid || pageTable[start].readOnly)
			return amount;
		if(start == spage) {
			amount += pageSize - vaddr + spage * pageSize;
			System.arraycopy(data, offset, memory, pageTable[spage].ppn * pageSize + vaddr - spage * pageSize, amount);
			pageTable[spage].used = true;
			pageTable[spage].dirty = true;
		} else {
			System.arraycopy(data, offset + amount, memory, pageTable[start].ppn * pageSize, pageSize);
			pageTable[start].used = true;
			pageTable[start].dirty = true;
			amount += pageSize;
		}
		
	}
	if(!pageTable[epage].valid || pageTable[epage].readOnly)
		return amount;
	if(epage > spage) {
		System.arraycopy(data, offset + amount, memory, pageTable[epage].ppn * pageSize, (leng + vaddr) % pageSize);
		amount += (leng + vaddr) % pageSize;
	}
	else if(epage == spage) {
		System.arraycopy(data, offset + amount, memory, pageTable[epage].ppn * pageSize + vaddr % pageSize, leng);
		amount += leng;
	}
	pageTable[epage].used = true;
	pageTable[epage].dirty = true;
	//amount += (leng + vaddr) % pageSize;
	/*
	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);
	*/
	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
    	UserKernel.pl.acquire();
	if (numPages > UserKernel.pages.size()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}
	
	pageTable = new TranslationEntry[numPages];
	for (int i=0; i<numPages; i++)
	    pageTable[i] = new TranslationEntry(i,UserKernel.pages.poll(), true,false,false,false);
	
	UserKernel.pl.release();

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;
		pageTable[vpn].readOnly = section.isReadOnly();

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, pageTable[vpn].ppn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	UserKernel.pl.acquire();
    	for (int i = 0; i < numPages; i++) {
			UserKernel.pages.add(pageTable[i].ppn);
		}
    	UserKernel.pl.release();
    	//for(int i = 0; i < 16; i++) {
    	//	if(files[i] != null)
    	//		handleClose(i);
    	//}
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    	if(processID != 0)
    		return -1;

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
    
    private int handleExec(int fileAdd, int argc, int argv) {
    	if(fileAdd < 0)
    		return -1;
    	String file = readVirtualMemoryString(fileAdd, 256);
    	if(file == null)
    		return -1;
    	if(!file.toLowerCase().endsWith(".coff"))
    		return -1;
    	if(argc < 0)
    		return -1;
    	int[] add = new int[argc];
    	for(int i = 0; i < argc; i++) {
    		byte[] data = new byte[4];
    		if(4 != readVirtualMemory(argv + 4*i, data))
    			return -1;
    		add[i] = 0;
    		for(int j = 0; j < 4; j++) {
    			add[i] = (add[i]|data[3-j]);
    			if(j != 3)
    				add[i] = add[i] << 8;
    		}
    	}
    	String[] args = new String[argc];
    	for(int i = 0; i < argc; i++) {
    		args[i] = readVirtualMemoryString(add[i], 256);
    		if(args[i] == null)
    			return -1;
    	}
    	UserProcess child = new UserProcess();
    	child.execute(file, args);
    	children.add(new Child(child, initialCode));
    	return child.processID;
    }
    
    private int handleJoin(int processID, int status) {
    	int size = children.size(), id = size;
    	for(int i = 0; i < size; i++) {
    		if(children.get(i).up.processID == processID) {
    			id = i;
    			break;
    		}
    	}
    	if(id >= size)
    		return -1;
    	int ec = children.get(id).exitCode;
    	if(ec != initialCode) {
    		byte[] data = new byte[4];
    		data[0] = (byte)(ec & 0xff);
    		data[1] = (byte)((0xff00 & ec) >> 8);
    		data[2] = (byte)((0xff0000 & ec) >> 16);
    		data[3] = (byte)((0xff000000 & ec) >> 24);
    		if(4 != writeVirtualMemory(status, data)) {
    			children.remove(id);
    			return 0;
    		}
    		if(children.get(id).exitCode == unhandledSyscall || children.get(id).exitCode == unhandledException) {
    			children.remove(id);
    			return 0;
    		}
    		children.remove(id);
    		return 1;
    	}
    	children.get(id).up.thread.join();
    	ec = children.get(id).exitCode;
    	byte[] data = new byte[4];
		data[0] = (byte)(ec & 0xff);
		data[1] = (byte)((0xff00 & ec) >> 8);
		data[2] = (byte)((0xff0000 & ec) >> 16);
		data[3] = (byte)((0xff000000 & ec) >> 24);
    	children.remove(id);
    	if(4 != writeVirtualMemory(status, data))
    		return 0;
    	return 1;
    }
    
    private int handleExit(int status) {
    	unloadSections();
    	for(int i = 0; i < children.size(); i++) {
    		children.get(i).up.parent = null;
    	}
    	if(this.parent != null) {
    		if(parent.setExitCode(this.processID, status) != 1)
    			return -1;
    	}
    	if(processID == 0)
    		Kernel.kernel.terminate();
    	thread.finish();
    	return 1;
    }
    
    private int setExitCode(int childID, int status) {
    	for(int i = 0; i < children.size(); i++) {
    		if(children.get(i).up.processID == childID) {
    			children.get(i).exitCode = status;
    			return 1;
    		}
    	}
    	return 0;
    }
    
    private int handleCreate(int address) {
		if (address < 0)
			return -1;
		String fileName = readVirtualMemoryString(address, 256);
		if (fileName == null)
			return -1;
		int allo = 0;
		for (; allo < 16; allo++) {
			if (files[allo] == null) {
				break;
			}
		}
		if (allo == 16) {
			return -1;
		}
		OpenFile createdFile = ThreadedKernel.fileSystem.open(fileName, true);
		if (createdFile == null) {
			return -1;
		} else if(!UserKernel.createFile(fileName)){
			return -1;
		} else {
			files[allo] = new FileDescriptor(fileName, createdFile);
			return allo;
		}
	}

	public int handleOpen(int address) {
		if (address < 0)
			return -1;
		String fileName = readVirtualMemoryString(address, 256);
		if (fileName == null)
			return -1;
		int allo = 0;
		for (; allo < 16; allo++) {
			if (files[allo] == null) {
				break;
			}
		}
		if (allo == 16) {
			return -1;
		}
		OpenFile createdFile = ThreadedKernel.fileSystem.open(fileName, false);
		if (createdFile == null) {
			return -1;
		} else if(!UserKernel.createFile(fileName)){
			return -1;
		} else {
			files[allo] = new FileDescriptor(fileName, createdFile);
			return allo;
		}
	}

	public int handleRead(int index, int address, int bufsize) {
		if (index < 0 || index >= 16)
			return -1;
		if(address < 0 || bufsize < 0)
			return -1;
		if(files[index] == null)
			return -1;

		FileDescriptor toRead = files[index];
		int read = 0;

		while (bufsize > 0) {
			byte[] data = new byte[Math.min(bufsize, maxBufSize)];
			bufsize -= data.length;
			int dataRead = toRead.file.read(data, 0, data.length);

			if (dataRead < 0) {
				return -1;
			} else {
				int numBytesNewlyWrited = writeVirtualMemory(address, data,
						0, dataRead);
				if (numBytesNewlyWrited < dataRead)
					return -1;
				read += dataRead;
				address += dataRead;
				if (dataRead < data.length)
					break;
			}
		}
		return read;
	}

	public int handleWrite(int index, int address, int bufsize) {
		if (index < 0 || index >= 16)
			return -1;
		if(address < 0 || bufsize < 0)
			return -1;
		if(files[index] == null)
			return -1;

		FileDescriptor ftoRead = files[index];
		int read = 0;

		while (bufsize > 0) {
			byte[] data = new byte[Math.min(bufsize, maxBufSize)];
			bufsize -= data.length;
			int toRead = readVirtualMemory(address, data);

			if (toRead < data.length) {
				return -1;
			} else {
				stdLock.acquire();
				int numBytesNewlyWrited = ftoRead.file.write(data, 0,
						data.length);
				stdLock.release();
				read += toRead;
				address += toRead;
				if (numBytesNewlyWrited < toRead)
					break;
			}

		}

		return read;
	}

	public int handleClose(int index) {
		if (index < 0 || index >= 16)
			return -1;
		if(files[index] == null)
			return -1;

		FileDescriptor toClose = files[index];
		String filename = toClose.filename;
		toClose.file.close();
		files[index] = null;

		if (UserKernel.closeFile(filename)) {
			return 0;
		}

		return -1;
	}

	public int handleUnlink(int address) {
		if (address < 0) {
			return -1;
		}

		String fileName = readVirtualMemoryString(address, 256);
		if (fileName == null)
			return -1;

		if (UserKernel.unlinkFile(fileName)) {
			return 0;
		}
		return -1;
	}


    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
		return handleExit(a0);
	case syscallExec:
		return handleExec(a0, a1, a2);
	case syscallJoin:
		return handleJoin(a0, a1);
	case syscallCreate:
		return handleCreate(a0);
	case syscallOpen:
		return handleOpen(a0);
	case syscallRead:
		return handleRead(a0, a1, a2);
	case syscallWrite:
		return handleWrite(a0, a1, a2);
	case syscallClose:
		return handleClose(a0);
	case syscallUnlink:
		return handleUnlink(a0);


	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	    handleExit(unhandledSyscall);
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	    handleExit(unhandledException);
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    private class Child {
    	private UserProcess up = null;
    	private int exitCode;
    	
    	public Child(UserProcess uprocess, int e) {
    		this.up = uprocess;
    		exitCode = e;
    	}
    }
    public class FileDescriptor {
		public String filename = null;
		public OpenFile file = null;

		public FileDescriptor(String filename, OpenFile file) {
			this.file = file;
			this.filename = filename;
		}
	}
    private final int maxBufSize = 1 << 20;
    private static Lock stdLock = null;
    private FileDescriptor[] files = null;
    private int processID;
    private static int process = 0;
    private UThread thread = null;
    private LinkedList<Child> children = null;
    //private Lock cl = new Lock();
    private UserProcess parent = null;
    private static int initialCode = -6583;
    private static int unhandledException = -6586;
    private static int unhandledSyscall = -4548;
    //private FileDescriptor[] files = new FileDescriptor[16];
}
