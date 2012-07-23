package cz.filmtit.dataimport.alignment.model.eval

import scala.collection.JavaConversions._
import cz.filmtit.dataimport.alignment.aligners.distance.DistanceChunkAlignment
import cz.filmtit.dataimport.alignment.aligners.distance.LinearSubtitlePairCounter
import cz.filmtit.dataimport.alignment.aligners.trivial.TrivialChunkAlignment
import cz.filmtit.dataimport.alignment.aligners.levensthein.LevenstheinChunkAlignment
import cz.filmtit.dataimport.database.Import
import cz.filmtit.dataimport.alignment.model.GoodFilePairChooserFromFile
import cz.filmtit.dataimport.alignment.model.SubtitleFileAlignmentFromFile
import cz.filmtit.dataimport.alignment.model.Aligner
import cz.filmtit.core.Configuration
import cz.filmtit.dataimport.alignment.model.SubtitleFile
import cz.filmtit.share.parsing.UnprocessedChunk
import collection.mutable.ListBuffer
import cz.filmtit.dataimport.alignment.model.ChunkAlignment
import java.io.File
import cz.filmtit.dataimport.SubtitleMapping
import cz.filmtit.share.Chunk
import cz.filmtit.share.MediaSource
import cz.filmtit.share.Language
import cz.filmtit.share.parsing.Parser
//import cz.filmtit.share.parsing.Parser.processChunk

class TMEvaluator(val c:Configuration, val alignedFiles:File, val chunkAlignment:ChunkAlignment, val numberOfTestedFiles:Int) {
    val l1 = Language.EN
    val l2 = Language.CS
    
    val mapping = new SubtitleMapping(c)
    def tested:Seq[String] = scala.util.Random.shuffle(mapping.moviesWithSubs).take(numberOfTestedFiles).toSeq
  
    def loadAlignedFiles():Map[String, Pair[SubtitleFile, SubtitleFile]] = 
        TMEvaluator.loadFilePairsToMap(alignedFiles, c)
 

   def loadAlignedFilesExceptTested():Map[String, Pair[SubtitleFile, SubtitleFile]] = 
        loadAlignedFiles--tested
    
   def alignFiles() {

        c.dataFolder.listFiles.foreach{_.delete()}
        
        val map = loadAlignedFilesExceptTested

        val aligner:Aligner = new Aligner(new SubtitleFileAlignmentFromFile(l1, l2, map), chunkAlignment, new GoodFilePairChooserFromFile(map), c, l1, l2)

        aligner.align(mapping)
   }

    def queryTMForStrings(sentences:Seq[String]):Seq[Iterable[String]] = {
        val set = sentences.toSet
        
        val resMap:Map[String, Iterable[String]] =
        
        c.dataFolder.listFiles.flatMap{
            filename => 
        //I have no fucking clue why is the fucking io.Source crashing=>rewriting as pure java
                import scala.collection.mutable.ListBuffer
                import java.io._
                
                val buf: ListBuffer[Pair[String,String]] = new ListBuffer[Pair[String,String]]()
                val fstream:FileInputStream = new FileInputStream(filename)
                val in: DataInputStream  = new DataInputStream(fstream);
                val br: BufferedReader = new BufferedReader(new InputStreamReader(in));
                var strLine:String ="";
                
                val reg = """(.*)\t(.*)""".r
                strLine = br.readLine
                while (strLine != null)   {
                    strLine match {
                        case reg(en,cz)=>
                            if (set.contains(en)) {
                                buf += ((en,cz))
                            }
                    }
                    strLine = br.readLine
                    
                }
                in.close
                
                buf                    
         }.groupBy{_._1}.mapValues{_.map{_._2}}
        
        sentences.map{
            s=>if(resMap.contains(s)){
                resMap(s).take(30)
            } else {
                val l:List[String] = List[String]()
                l
            }
        }
    }

    def loadTestedFiles():Seq[String] = {
        val files:Seq[SubtitleFile] = tested.map {
           moviename=>
              mapping.getSubtitles(moviename).get.filter{_.language==l1}.minBy{_.fileNumber.toInt}
        }
        val unprocessedChunks:Seq[UnprocessedChunk] = files.flatMap {
            _.readChunks.slice(60,70)
        }
        
        val processedChunks:Seq[Chunk] = unprocessedChunks.flatMap {
            Parser.processChunk(_, 0, 0L, l1)
        }

        val res:Seq[String] = processedChunks.map {
            _.getSurfaceForm
        }
        res
    }



}

object TMEvaluator {

        val l1 = Language.EN
        val l2 = Language.CS

    def loadFilePairsToMap(f:File, c:Configuration):Map[String, Pair[SubtitleFile, SubtitleFile]] = {
        import cz.filmtit.dataimport.alignment.model.Aligner.readFilePairsFromFile
        
        //for reasons I don't remember anymore it's switched here
        //(noone really cares here)
        val iterable = readFilePairsFromFile(f, c, l2, l1, false)

        iterable.map{case(f1,f2)=>(f1.filmID, (f1,f2))}.toMap
    }
    def readCountInfo(source:Seq[String], targets:Seq[Iterable[String]]):Seq[Int] = {
        (0 to source.size-1).flatMap {
            i=>
            val input_real = if (targets(i).size > 0) {

                println("=========")
                println(source(i))
                println("--------")
                targets(i).foreach{println(_)}
                println("========")
                var done = false
                var input = 0
                while (!done){
                    println("enter")
                    
                    try {
                        input = readLine.toInt
                        done = true
                    } catch {
                        case e:Exception=>done = false
                    }
                }
                input
            } else {
                0
            }
            
            
            Array(input_real, targets(i).size)
        }
    }

    def saveCountInfo(where:File, what:Seq[Int]) = {
        val writer = new java.io.PrintWriter(where)
        what.foreach{writer.println(_)}
        writer.close
    }

    def doComparison(descriptions: Iterable[Tuple3[String, ChunkAlignment, String]]) {
        descriptions.foreach {
            case(file2file, chunk2chunk, results) =>
               val evaluator = new TMEvaluator(new Configuration("configuration.xml"), new File(file2file), chunk2chunk, 30)
               evaluator.alignFiles
               val testedS = evaluator.loadTestedFiles
               val testedT = evaluator.queryTMForStrings(testedS)
               saveCountInfo(new File(results), readCountInfo(testedS, testedT))
        }
    }

    def countPercentages() {
        //hardcoded, but I run it only once anyway
        val res = new java.io.File("../res")

        res.listFiles.foreach {
            name=>
            var pr_number = 0;
            var predcitam = true
            var all_subs = 0
            var covered_subs = 0
            var all_sent = 0
            var all_correct = 0
            io.Source.fromFile(name).getLines.foreach {
                line=>
        
                val loaded_int = line.toInt
                if (predcitam) {
                    pr_number = loaded_int
                    predcitam = false
                } else {
                    val titlessent = loaded_int
                    val titlescorrect = if (pr_number>=0) {pr_number} else {titlessent+pr_number+1}
                    all_subs+=1
                    if (titlescorrect>0) {covered_subs+=1}
                    
                    all_correct+=titlescorrect
                    all_sent+=titlessent
                    //if (titlescorrect>titlessent) {println("WTF - "+titlescorrect+" - "+titlessent+ "- filename"+name)}
                    predcitam=true
                }
                    
            }
            
            val precision = all_correct.toFloat/all_sent
            val coverage = covered_subs.toFloat/all_subs
            val harmean = 2*precision*coverage/(precision+coverage)

            println(name+"\t "+(precision*100).toInt+"\t"+(coverage*100).toInt+"\t"+(harmean*100).toInt)

        } 
    }

    def final_alignment() {
        val c = new Configuration("configuration.xml")
        val l1 = Language.EN
        val l2 = Language.CS
        
        val mapping = new SubtitleMapping(c)

        val cnt = new LinearSubtitlePairCounter
        val filename ="../alignment_file2file/distance12k" 
        val alignment =  new DistanceChunkAlignment(l1, l2, cnt)
        
        val file = new File(filename)
        val map = loadFilePairsToMap(file, c)

        val aligner:Aligner = new Aligner(new SubtitleFileAlignmentFromFile(l1, l2, map), alignment, new GoodFilePairChooserFromFile(map), c, l1, l2)
        aligner.align(mapping)
    
    }

    def main(args: Array[String]) {

        if(true){final_alignment(); return}
        val cnt = new LinearSubtitlePairCounter

        val ar:Array[Tuple3[String, ChunkAlignment, String]] = Array (
            ("../alignment_file2file/leven",  new LevenstheinChunkAlignment(l1, l2, 6000L), "../res/leven6k"),
            ("../alignment_file2file/leven",  new LevenstheinChunkAlignment(l1, l2, 600L), "../res/leven600"),
            ("../alignment_file2file/distance",  new LevenstheinChunkAlignment(l1, l2, 6000L), "../res/leven6k_d"),
            ("../alignment_file2file/distance12k",  new LevenstheinChunkAlignment(l1, l2, 6000L), "../res/leven6k_d12k"),
            ("../alignment_file2file/leven",  new DistanceChunkAlignment(l1, l2, cnt), "../res/distance_leven"),
            ("../alignment_file2file/distance",  new DistanceChunkAlignment(l1, l2, cnt), "../res/distance"),
            ("../alignment_file2file/distance12k",  new DistanceChunkAlignment(l1, l2, cnt), "../res/distance12k"),
            ("../alignment_file2file/trivial",  new TrivialChunkAlignment(l1, l2), "../res/trivial")
    )
            
    doComparison(ar)          
     //println(evaluator.queryTMForStrings(Array("Hello", "Who are you?")))
  }
} 