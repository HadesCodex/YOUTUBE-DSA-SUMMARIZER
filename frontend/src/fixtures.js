// A realistic API response, used by the tests only (the app never imports this file).
export function sampleResult(overrides = {}) {
  return {
    videoId: 'MHf6awe89xw',
    wordCount: 6325,
    source: 'fetched',
    modelUsed: 'gemini-3.8-flash',
    stats: { totalWords: 6325, chunksCreated: 10, chunksKept: 8, wordsSent: 5025 },
    usage: { inputTokens: 6147, outputTokens: 568, thoughtTokens: 0, totalTokens: 6715 },
    summary: {
      topic: 'Binary Search',
      problemStatement: 'Find the index of a target in a sorted array, or return -1.',
      algorithmSteps: ['Set low and high.', 'Compute mid.', 'Move low or high.'],
      pseudocode: 'low = 0\nhigh = n - 1\nwhile low <= high:\n    mid = low + (high - low) / 2',
      timeComplexity: { value: 'O(log n)', explanation: 'The range halves each step.' },
      spaceComplexity: {
        value: 'Not covered in the provided portion of the lecture',
        explanation: 'Not covered in the provided portion of the lecture',
      },
      edgeCases: ['Empty array', 'Target missing'],
      revisionSummary: ['Line 1', 'Line 2', 'Line 3', 'Line 4', 'Line 5'],
    },
    ...overrides,
  }
}
