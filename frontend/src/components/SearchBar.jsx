export default function SearchBar({ value, onChange }) {
  return (
    <input
      className="search"
      type="search"
      placeholder="Search titles, file names and text"
      aria-label="Search documents"
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}
