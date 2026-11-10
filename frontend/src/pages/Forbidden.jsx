import { Link } from "react-router";

export const MSG13 = "Bạn không có quyền thực hiện thao tác này.";

export default function Forbidden() {
    return (
        <main className="min-h-screen flex flex-col items-center justify-center bg-gray-50 px-4 text-center">
            <h1 className="text-9xl font-bold text-teal-600">403</h1>

            <h2 className="mt-4 text-2xl font-semibold text-gray-800">
                Không có quyền truy cập
            </h2>

            <p role="alert" className="mt-2 text-gray-500">
                {MSG13}
            </p>

            <Link
                to="/"
                className="mt-6 rounded-lg bg-teal-600 px-6 py-3 font-medium text-white transition hover:bg-teal-700"
            >
                Quay về trang chủ
            </Link>
        </main>
    );
}
