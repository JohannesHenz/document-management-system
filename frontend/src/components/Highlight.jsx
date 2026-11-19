/** Renders text with every occurrence of the query's words wrapped in <mark>. */
export default function Highlight({ text, query }) {
  const words = query
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (words.length === 0) return text;

  const pattern = new RegExp(`(${words.join('|')})`, 'gi');
  // split() with a capturing group puts the matches at the odd indexes
  return text.split(pattern).map((part, index) => (index % 2 === 1 ? <mark key={index}>{part}</mark> : part));
}
