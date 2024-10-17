// Example of usage:

//   1. Import native module

//   import DocumentsHandler, {DocumentsHandlerResult} from './DocumentsHandler';

//  2. Get paths to documents, that should be processed. The document must be one of the listed formats: pdf, jpg, jpeg, png, heic
//   const source1 = 'file://.../image1.jpeg';
//   const source2 = 'file://.../image2.jpeg';
//   const source3 = 'file://.../book1.pdf';
//   const source4 = 'file://.../book2.pdf';

//   3. Call as async method, set grayscale and expected width for the result images:
  
//   const onPress = async () => {
//     await DocumentsHandler.process({
//        documents: [source1, source2, source3, source4],
//        grayscale: true,
//        expectedWidth: 100
//      })
//        .then(res => {
//         console.log('RESULTS: ', res);
//       })
//        .catch(error => {
//         console.log(error.message);
//        });    
//   };

import {NativeModules} from 'react-native';
import type { Float } from 'react-native/Libraries/Types/CodegenTypes';

interface Result {
  index: number
  incoming: string
  outcoming: number[]
}

export interface DocumentsHandlerRequest {
  documents: string[];
  grayscale: true;
  expectedWidth: Float;
}

export interface DocumentsHandlerResult {
  result: Result[];
}

export interface DocumentsHandler {
  process: (request: DocumentsHandlerRequest) => Promise<DocumentsHandlerResult>;
}

export default NativeModules.RNDocumentsHandler as DocumentsHandler;