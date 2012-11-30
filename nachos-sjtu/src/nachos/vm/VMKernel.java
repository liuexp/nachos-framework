package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.userprog.UserKernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = fileSystem.open(swapFileName,true);
//		swapLock = new Lock();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		swapFile = null;
		swapTable.clear();
		swapFree.clear();
		swapSize = 0;
		fileSystem.remove(swapFileName);
		super.terminate();
	}
	
	/**
	 * Return the ppn that's freed.
	 */
	public int swapOut(){
		Lib.assertTrue(VMProcess.tableLock.isHeldByCurrentThread());
		Processor p = Machine.processor();
		byte [] memory = p.getMemory();
		int ppn=-1;
		boolean flag = true;
		int luckyNumber = 3;
		int currentLuck = 0;
		while(flag&&currentLuck < luckyNumber){
			 ppn = Lib.random(Machine.processor().getNumPhysPages());
			 flag= false;
			 for(int i=0;i<p.getTLBSize();i++){
					if(p.readTLBEntry(i).ppn == ppn)flag = true;
			}
			 currentLuck++;
		}
		//invalidate TLB entry
		if(flag){
			Lib.debug(dbgVM, "[Luck]Not so lucky!");
			 for(int i=0;i<p.getTLBSize();i++){
					if(p.readTLBEntry(i).ppn == ppn){
						putTLBEntry(i, VMProcess.nullEntry);
					}
			}
		}
		
//		phyTableLock.acquire();
		VMPage page = phyTable[ppn];
		TranslationEntry e = ipTable.get(page);
		if(e==null){
			Lib.assertNotReached("swapOut here?");
		}
		TranslationEntry tmp = ipTable.remove(page);
		Lib.assertTrue(tmp != null);
		
		Lib.debug(dbgVM, "[SWAP out chosen]"+ppn + " ," + page.toString());

		if(!e.readOnly && e.dirty){
			Lib.debug(dbgVM, "[SWAP out]"+ppn + " ," + page.toString());
//			swapLock.acquire();
			Integer pos = swapTable.get(page);
			if(pos == null){
				if(swapFree.isEmpty()){
					pos = swapSize;
					swapSize += pageSize;
				}else{
					pos = swapFree.poll();
				}
			}
			swapFile.write(pos, memory, Processor.makeAddress(ppn, 0), pageSize);
			swapTable.put(page, pos);
//			swapLock.release();
		}
		e.valid = false;
		e.ppn = -1;
		phyTable[ppn] = null;
//		phyTableLock.release();
		return ppn;
	}
	
	public void putTLBEntry(int i, TranslationEntry e){
		//Lib.assertTrue(VMProcess.tableLock.isHeldByCurrentThread());
		Processor p = Machine.processor();
		TranslationEntry oldEntry = p.readTLBEntry(i);
		if(oldEntry!= null&& oldEntry.valid){
			TranslationEntry oldEntry2 = VMKernel.getKernel().ipTable.get(UserKernel.phyTable[oldEntry.ppn]);
			if(oldEntry2!=null){
				oldEntry2.used |= oldEntry.used;
				oldEntry2.dirty |= oldEntry.dirty;
			}
		}
		p.writeTLBEntry(i, e);
	}
	
	/**
	 * @Warning please allocate a page before calling me.
	 * @param page
	 */
	public boolean swapIn(VMPage page){
		//Lib.assertTrue(VMProcess.tableLock.isHeldByCurrentThread());
		Processor p = Machine.processor();
//		swapLock.acquire();
		Integer pos = swapTable.get(page);
		TranslationEntry e = ipTable.get(page);
		if(pos == null || e == null){
			Lib.debug(dbgVM, "[SWAP in fault]"+page.toString());
//			swapLock.release();
			return false;
		}
		Lib.debug(dbgVM, "[SWAP in chosen]"+e.ppn + " ," + page.toString());
		byte [] memory = p.getMemory();
		swapFile.read(pos, memory, Processor.makeAddress(e.ppn, 0), pageSize);
//		swapLock.release();
		return true;
	}
	
	public void freeSwap(VMProcess p){
//		swapLock.acquire();
		LinkedList<VMPage> toBeFreed = new LinkedList<VMPage> ();
		for(VMPage x : swapTable.keySet()){
			if(x.pid == p.pid)
				toBeFreed.add(x);
		}
		
		for(VMPage x : toBeFreed){
			swapFree.add(swapTable.remove(x));
		}
//		swapLock.release();
	}
	
	public static VMKernel getKernel() {
		return (VMKernel) kernel;
	}
	
	private static final char dbgVM = 'v';
	private static final int pageSize = Processor.pageSize;
	public HashMap<VMPage, TranslationEntry> ipTable = new HashMap<VMPage, TranslationEntry> ();
	
	public OpenFile swapFile;
	public HashMap<VMPage, Integer> swapTable = new HashMap<VMPage, Integer> ();
	public LinkedList<Integer> swapFree = new LinkedList<Integer> ();
	public int swapSize = 0;
	//public Lock swapLock;
	public static final String swapFileName = "SWAP";
}
