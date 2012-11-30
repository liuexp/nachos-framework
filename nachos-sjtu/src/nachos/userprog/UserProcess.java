package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.VMPage;
import nachos.vm.VMProcess;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		UserKernel.pidCountLock.acquire();
		pid = UserKernel.pidCount++;
		UserKernel.pidCountLock.release();
		
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		UserKernel.processLock.acquire();
		UserKernel.processCnt++;
		UserKernel.processLock.release();
		new UThread(this).setName(name).fork();

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
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return copyVirtualMemory(vaddr, data, offset, length, true);
	}
	
	public int copyVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean read){
		try{
			Lib.assertTrue(offset >= 0 && length >= 0
					&& offset + length <= data.length);
			Processor p = Machine.processor();
			byte[] memory  = p.getMemory();
	
			if(vaddr < 0)handleException(Processor.exceptionAddressError);
			int curVpn = Processor.pageFromAddress(vaddr);
			int curOffset = offset;
			int tot = 0;
			int vOffset = Processor.offsetFromAddress(vaddr);
			while(length > 0){
				int ppn = pageTable[curVpn].valid? pageTable[curVpn].ppn : -1;
				ppn = (read||!pageTable[curVpn].readOnly)?ppn:-1;
				int paddr = Processor.makeAddress(ppn, vOffset); 
				int amount = Math.min(length, pageSize - vOffset);
				if (paddr < 0 || paddr >= memory.length || amount < 0)break; 
				
				if(read)
					System.arraycopy(memory, paddr, data, curOffset, amount);
				else
					System.arraycopy(data, curOffset, memory, paddr, amount);
				
				curOffset += amount;
				
				tot += amount;
				curVpn++;
				length -= amount;
				vOffset = 0;
			}
	
			return tot;
		} catch (Exception e){
			return 0;
		} finally{
			//do nothing
		}
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return copyVirtualMemory(vaddr, data, offset, length, false);
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
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
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
			Lib.debug(dbgProcess, section.getName()+":"+section.getFirstVPN());
		}
		Lib.debug(dbgProcess, "number of pages:"+numPages);

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
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
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;
		
		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib
					.assertTrue(writeVirtualMemory(entryOffset,
							stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset,
							new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory numPages="+numPages +", PhysPages="+Machine.processor().getNumPhysPages());
			return false;
		}
		
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			allocPage(i);
			//pageTable[i] = new TranslationEntry(i, i, false, false, false, false);

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				int ppn = pageTable[vpn].ppn;//allocPage(vpn, section.isReadOnly());
				pageTable[vpn].readOnly = section.isReadOnly();
				
				section.loadPage(i, ppn);
			}
		}

		return true;
	}

	protected int allocPage(int vpn) {
		int ppn = -1;
		int pn = Machine.processor().getNumPhysPages();
		
		for(int i=0;i<pn;i++){
			UserKernel.phyTableLock.acquire();
			if(UserKernel.phyTable[i] == null){
				UserKernel.phyTable[i]=new VMPage(this.pid, vpn);
				ppn=i;
				UserKernel.phyTableLock.release();
				break;
			}
			UserKernel.phyTableLock.release();
		}
		if(ppn<0)handleException(Processor.exceptionBusError);
		pageTable[vpn]=new TranslationEntry(vpn,ppn,true,false,false,false);
		return ppn;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (TranslationEntry e : pageTable){
			if(e.valid){
				UserKernel.phyTableLock.acquire();
				UserKernel.phyTable[e.ppn] = null;
				UserKernel.phyTableLock.release();
				e.valid=false;
			}
		}
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
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
		if(pid == UserKernel.pidMain){
			Machine.halt();
			Lib.assertNotReached("Machine.halt() did not halt machine!");
		}
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
		case syscallJoin:
			return handleJoin(a0,a1);
		case syscallCreate: 
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			unHandledException = true;
			//Lib.assertNotReached("Unknown system call!");
			return -1;
		}
	}

	private int handleUnlink(int a0) {
		try {
			String name = readVirtualMemoryString(a0,256);
			if (ThreadedKernel.fileSystem.remove(name)){
				return 0;
			}else
				return -1;
		} catch (Exception e){
			return -1;
		}
	}

	private int handleClose(int a0) {
		try{
			UserKernel.fdLock.acquire();
			OpenFile h =UserKernel.fileDescriptors.get(a0); 
			if(h!=null)h.close();
			h.getName();
			UserKernel.fileDescriptors.set(a0, null);
		} catch(Exception e){
			return -1;
		} finally{
			if(UserKernel.fdLock.isHeldByCurrentThread())
				UserKernel.fdLock.release();
		}
		return 0;
	}

	private int handleWrite(int a0, int a1, int a2) {
		try{
			UserKernel.fdLock.acquire();
			OpenFile f = UserKernel.fileDescriptors.get(a0);
			UserKernel.fdLock.release();
			byte [] buf = new byte[a2];
			readVirtualMemory(a1,buf);
			return f.write(buf, 0, a2);
		} catch (Exception e){
			return -1;
		} finally{
			if(UserKernel.fdLock.isHeldByCurrentThread())
				UserKernel.fdLock.release();
		}
	}

	private int handleRead(int a0, int a1, int a2) {
		try{
			UserKernel.fdLock.acquire();
			OpenFile f = UserKernel.fileDescriptors.get(a0);
			UserKernel.fdLock.release();
			byte [] buf = new byte[a2];
			int ret = f.read(buf, 0, a2);
			return Math.min(ret, writeVirtualMemory(a1,buf));
		} catch (Exception e){
			return -1;
		} finally {
			if(UserKernel.fdLock.isHeldByCurrentThread())
				UserKernel.fdLock.release();
		}
	}

	private int handleOpen(int a0) {
		try{
			String name = readVirtualMemoryString(a0,256);
			OpenFile h = UserKernel.fileSystem.open(name, false);
			if(h == null) return -1;
			UserKernel.fdLock.acquire();
			UserKernel.fileDescriptors.add(h);
			int ret = UserKernel.fileDescriptors.size()-1;
			UserKernel.fdLock.release();
			curfd.add(ret);
			return ret;
		} catch (Exception e){
			return -1;
		} finally{
			if(UserKernel.fdLock.isHeldByCurrentThread())
				UserKernel.fdLock.release();
		}
	}

	private int handleCreate(int a0) {
		try{
			String name = readVirtualMemoryString(a0,256);
			OpenFile h = UserKernel.fileSystem.open(name, true);
			if(h == null) return -1;
			UserKernel.fdLock.acquire();
			UserKernel.fileDescriptors.add(h);
			int ret = UserKernel.fileDescriptors.size()-1;
			UserKernel.fdLock.release();
			curfd.add(ret);
			return ret;
		} catch (Exception e){
			return -1;
		} finally{
			if(UserKernel.fdLock.isHeldByCurrentThread())
				UserKernel.fdLock.release();
		}
	}

	private int handleJoin(int a0, int a1) {
		childrenLock.acquire();
		UserProcess c = children.get(a0);
		Integer ret = -1;
		Integer status = childrenStatus.get(a0);
		if(c != null){
			if(status == null){
				toBeJoined = c;
				children.remove(c);
				//here automatically releases childrenLock
				childrenDead.sleep();
				status = childrenStatus.get(a0);
			}
			if(status!=null){
				if(writeVirtualMemory(a1,Lib.bytesFromInt(status)) == intByteSize){
					ret = status.equals(-1)?0:1;
				}else 
					ret = 0;
			}else{
				ret = 0;
			}
		}
		toBeJoined = null;
		childrenLock.release();
		return ret;
	}

	private int handleExec(int a0, int a1, int a2) {
		try{
			String name = readVirtualMemoryString(a0,256);
			if(!name.endsWith("coff"))return -1;
			UserProcess c = newUserProcess();
			String [] args = new String[a1];
			for(int i=0;i<a1;i++){
				byte [] buf = new byte [intByteSize];
				if(readVirtualMemory(a2 + i*intByteSize, buf) != buf.length){
					return -1;
				}
				int addr = Lib.bytesToInt(buf, 0);
				args[i] = readVirtualMemoryString(addr,256);
			}
			c.parent = this;
			childrenLock.acquire();
			children.put(c.pid, c);
			childrenLock.release();
			boolean ret = c.execute(name, args);
			if(!ret){
				childrenLock.acquire();
				children.remove(c.pid);
				childrenLock.release();
				return -1;
			}
			return c.pid;
		} catch (Exception e){
			//e.printStackTrace();
			return -1;
		} finally {
			if(childrenLock.isHeldByCurrentThread())
				childrenLock.release();
		}
	}

	private int handleExit(int a0) {
		for (UserProcess c : children.values()){
			c.parent = null;
		}
		if(parent!=null){
			parent.childrenLock.acquire();
			if(!unHandledException)
				parent.childrenStatus.put(pid, a0);
			if(parent.toBeJoined == this){
				parent.toBeJoined = null;
				parent.childrenDead.wake();
			}
			parent.childrenLock.release();
		}
		unloadSections();
		for (Integer i: curfd){
			handleClose(i);
		}
		
		UserKernel.processLock.acquire();
		UserKernel.processCnt--;
		UserKernel.processLock.release();
		if (UserKernel.processCnt<=0) 
			Kernel.kernel.terminate();
		//if (pid == UserKernel.pidMain)Machine.halt();
		UThread.finish();
		return a0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0), processor
							.readRegister(Processor.regA1), processor
							.readRegister(Processor.regA2), processor
							.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;
		case Processor.exceptionAddressError:
		case Processor.exceptionBusError:
		case Processor.exceptionIllegalInstruction:
		case Processor.exceptionOverflow:
		case Processor.exceptionPageFault:
		case Processor.exceptionReadOnly:
		case Processor.exceptionTLBMiss:
			Lib.debug(dbgProcess, "SIGKILL exception: "
					+ Processor.exceptionNames[cause] + " for process " + pid);
			VMProcess.dumpTLB();
			VMProcess.dumpPageTable();
			handleExit(-1);
			break;
		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			unHandledException = true;
			//Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = Config.getInteger("Processor.numStackPages", 8);

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final int intByteSize = 4;
	
	public ArrayList<Integer> curfd = new ArrayList<Integer> ();
	public UserProcess toBeJoined;
	public UserProcess parent;
	public Lock childrenLock = new Lock();
	public Condition childrenDead = new Condition(childrenLock);
	public Map<Integer, UserProcess> children = new HashMap<Integer, UserProcess>();
	public Map<Integer, Integer> childrenStatus = new HashMap<Integer, Integer>();
	public boolean unHandledException = false;
	
	public int pid;
	
}
