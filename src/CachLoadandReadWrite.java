
        public void load(long address, boolean verbose){
               
                boolean found = false;
                boolean hit = false;
               
                operations++;
                long memoryBlock = address/blockSize; //The block in main memory
                int block = (int)(memoryBlock%blocks); //The block in cache to put data
                int tag = (int)memoryBlock/blocks; //The tag of the memoryBlock
               
                if(verbose){
                        System.out.println("Operation: "+operations);
                        System.out.printf("Processor load address", cacheNumber, address);
                        System.out.printf("Looking for word", address, tag, block);
                }
                if(!used[block]){ //If this line is empty
                       
                        for(int i = 0; i < cache[block].length; i++){
                                cache[block][i] = memoryBlock*blockSize+i;
                                tags[block] = tag;
                                used[block] = true;
                        }
                       
                        //set status of this cache block to be modified
                        status[block] = 2;
                       
                        }
                        if(verbose){
                                System.out.println("Read Miss");
                                System.out.printf("Loading", block);
                        }
                        found = true;
                }
                // Check if this block is in the cache
                // If we found read hit
                
                if(tag == tags[block]){
                        if(verbose){
                                System.out.printf("Found tag", tag, block);
                        }
                        hit = true;
                        hits++;
                        found = true;
                }
               
                if(state[block] == 0){ // if cache line is in invalid state
                        for(int i = 0; i < otherCaches.length; i++){
                                if(otherCaches[i].getTags()[block] == tag){
                                        //if it's in another cache, load into cache and go to shared state
                                        for(int j = 0; j < cache[block].length; j++){
                                                cache[block][j] = memoryBlock*blockSize+j;
                                                tags[block] = tag;
                                                used[block] = true;
                                        }
                                        state[block] = 1;
                                        found = true;
                                        break;
                                }
                        }
                       
                        if(!found){
                                //No other cache has this block
                                for(int j = 0; j < cache[block].length; j++){
                                        cache[block][j] = memoryBlock*blockSize+j;
                                        tags[block] = tag;
                                        used[block] = true;
                                }
                                status[block] = 3;
                                found = true;
                        }
                        
                }
               
               
                // Read Miss
                if(!found){
                        //from memory to the cache
                        for(int i = 0; i < cache[block].length; i++){
                                cache[block][i] = memoryBlock*blockSize+i;
                                tags[block] = tag;
                                used[block] = true;
                        }
                       
                        
                        if(verbose){
                                System.out.println("Read Miss");
                                System.out.printf("Loading block into chach", block);
                        }
                }
               
                
               
                //Notify other caches of this operation
                for(int i = 0; i < otherCaches.length; i++){
                        otherCaches[i].remoteLoad(address, hit, verbose);
                }
               
        }
       