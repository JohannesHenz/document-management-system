export default function SearchBar({ value, onChange }) {
  return (
    <input
      className="search"
      type="search"
      placeholder="Search by title or file name"
      aria-label="Search documents"
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}
